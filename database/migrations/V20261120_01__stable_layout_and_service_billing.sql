-- DEC-016 (lecturer feedback 2026-11-20): stable layout with fixed capacity + Horse Owner service billing paid by VNPay sandbox.
-- Additive: new tables, new nullable/defaulted columns on stable_box. No business row is deleted.
-- Existing boxes A/B/C-01..20 are placed on the 3 default yards (2 rows x 10 columns). Free boxes outside the layout become
-- RETIRED; an occupied box outside the layout keeps its horse and is reported as "outside the layout" until the horse is moved.
-- Dry-run on a backup or local copy first; never run implicitly at application startup.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
BEGIN TRY
    BEGIN TRANSACTION;

    /* ---------- Stable layout ---------- */
    IF OBJECT_ID(N'dbo.stable_yard', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.stable_yard (
            id                  BIGINT IDENTITY(1,1) NOT NULL,
            yard_code           VARCHAR(5)     NOT NULL,
            name                NVARCHAR(100)  NOT NULL,
            row_count           INT            NOT NULL,
            column_count        INT            NOT NULL,
            created_by_user_id  BIGINT         NULL,
            created_at          DATETIME2(3)   NOT NULL CONSTRAINT df_stable_yard__created_at DEFAULT SYSUTCDATETIME(),
            updated_at          DATETIME2(3)   NULL,
            CONSTRAINT pk_stable_yard PRIMARY KEY (id),
            CONSTRAINT uq_stable_yard__yard_code UNIQUE (yard_code),
            CONSTRAINT ck_stable_yard__yard_code CHECK (yard_code COLLATE Latin1_General_BIN LIKE '[A-Z]'),
            CONSTRAINT ck_stable_yard__row_count CHECK (row_count BETWEEN 1 AND 4),
            CONSTRAINT ck_stable_yard__column_count CHECK (column_count BETWEEN 1 AND 10),
            CONSTRAINT fk_stable_yard__created_by FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user(id)
        );
    END;

    IF COL_LENGTH(N'dbo.stable_box', N'stable_yard_id') IS NULL
    BEGIN
        ALTER TABLE dbo.stable_box ADD
            stable_yard_id BIGINT NULL,
            row_no         INT NULL,
            column_no      INT NULL,
            box_status     VARCHAR(20) NOT NULL CONSTRAINT df_stable_box__box_status DEFAULT 'ACTIVE';
        EXEC(N'ALTER TABLE dbo.stable_box ADD
            CONSTRAINT fk_stable_box__stable_yard FOREIGN KEY (stable_yard_id) REFERENCES dbo.stable_yard(id),
            CONSTRAINT ck_stable_box__box_status CHECK (box_status IN (''ACTIVE'',''MAINTENANCE'',''RETIRED'')),
            CONSTRAINT ck_stable_box__position CHECK (
                   (stable_yard_id IS NULL AND row_no IS NULL AND column_no IS NULL)
                OR (stable_yard_id IS NOT NULL AND row_no >= 1 AND column_no >= 1))');
        EXEC(N'CREATE UNIQUE INDEX ux_stable_box__yard_position ON dbo.stable_box (stable_yard_id, row_no, column_no)
            WHERE stable_yard_id IS NOT NULL');
    END;

    -- A box position must lie inside its yard grid. A CHECK constraint (not a trigger) keeps INSERT ... OUTPUT usable.
    -- Yard dimensions never change after creation (no API updates them), so checking the box row is sufficient.
    IF OBJECT_ID(N'dbo.fn_stable_box_position_in_yard', N'FN') IS NULL
        EXEC(N'CREATE FUNCTION dbo.fn_stable_box_position_in_yard (@yard_id BIGINT, @row_no INT, @column_no INT)
        RETURNS BIT WITH SCHEMABINDING AS
        BEGIN
            RETURN CASE
                WHEN @yard_id IS NULL THEN 1
                WHEN EXISTS (SELECT 1 FROM dbo.stable_yard y WHERE y.id = @yard_id
                             AND @row_no BETWEEN 1 AND y.row_count AND @column_no BETWEEN 1 AND y.column_count) THEN 1
                ELSE 0 END;
        END');
    IF OBJECT_ID(N'dbo.ck_stable_box__position_in_yard', N'C') IS NULL
        EXEC(N'ALTER TABLE dbo.stable_box ADD CONSTRAINT ck_stable_box__position_in_yard
            CHECK (dbo.fn_stable_box_position_in_yard(stable_yard_id, row_no, column_no) = 1)');

    INSERT dbo.stable_yard (yard_code, name, row_count, column_count)
    SELECT v.yard_code, v.name, 2, 10
    FROM (VALUES ('A', N'Khu chuồng A'), ('B', N'Khu chuồng B'), ('C', N'Khu chuồng C')) v(yard_code, name)
    WHERE NOT EXISTS (SELECT 1 FROM dbo.stable_yard y WHERE y.yard_code = v.yard_code);

    -- Place existing boxes X-NN (NN 1..row_count*column_count) of a default yard, numbered row by row.
    EXEC(N'UPDATE b SET
            stable_yard_id = y.id,
            row_no = (CAST(RIGHT(b.box_code, 2) AS INT) - 1) / y.column_count + 1,
            column_no = (CAST(RIGHT(b.box_code, 2) AS INT) - 1) % y.column_count + 1,
            yard = y.name,
            updated_at = SYSUTCDATETIME()
        FROM dbo.stable_box b
        JOIN dbo.stable_yard y ON y.yard_code = LEFT(b.box_code, 1)
        WHERE b.stable_yard_id IS NULL
          AND b.box_code COLLATE Latin1_General_BIN LIKE ''[A-Z]-[0-9][0-9]''
          AND CAST(RIGHT(b.box_code, 2) AS INT) BETWEEN 1 AND y.row_count * y.column_count
          AND NOT EXISTS (SELECT 1 FROM dbo.stable_box o WHERE o.stable_yard_id = y.id
                          AND o.row_no = (CAST(RIGHT(b.box_code, 2) AS INT) - 1) / y.column_count + 1
                          AND o.column_no = (CAST(RIGHT(b.box_code, 2) AS INT) - 1) % y.column_count + 1)');

    -- Fill every empty cell of every yard, so a yard always has exactly row_count x column_count boxes.
    EXEC(N'WITH n AS (SELECT TOP (40) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS k FROM sys.all_objects)
        INSERT dbo.stable_box (box_code, yard, stable_yard_id, row_no, column_no, box_status)
        SELECT CONCAT(y.yard_code, ''-'', RIGHT(CONCAT(''0'', n.k), 2)), y.name, y.id,
               (n.k - 1) / y.column_count + 1, (n.k - 1) % y.column_count + 1, ''ACTIVE''
        FROM dbo.stable_yard y
        JOIN n ON n.k <= y.row_count * y.column_count
        WHERE NOT EXISTS (SELECT 1 FROM dbo.stable_box o WHERE o.stable_yard_id = y.id
                          AND o.row_no = (n.k - 1) / y.column_count + 1 AND o.column_no = (n.k - 1) % y.column_count + 1)
          AND NOT EXISTS (SELECT 1 FROM dbo.stable_box c WHERE c.box_code = CONCAT(y.yard_code, ''-'', RIGHT(CONCAT(''0'', n.k), 2)))');

    -- A free box outside every yard is retired (kept for history, never assignable).
    EXEC(N'UPDATE b SET box_status = ''RETIRED'', updated_at = SYSUTCDATETIME()
        FROM dbo.stable_box b
        WHERE b.stable_yard_id IS NULL AND b.box_status <> ''RETIRED''
          AND NOT EXISTS (SELECT 1 FROM dbo.horse_stable_assignment a WHERE a.stable_box_id = b.id AND a.effective_to IS NULL)');

    /* ---------- Service billing ---------- */
    IF OBJECT_ID(N'dbo.service_price', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.service_price (
            id                  BIGINT IDENTITY(1,1) NOT NULL,
            service_code        VARCHAR(30)    NOT NULL,
            unit_price          DECIMAL(19,4)  NOT NULL,
            currency_code       CHAR(3)        NOT NULL CONSTRAINT df_service_price__currency_code DEFAULT 'VND',
            created_by_user_id  BIGINT         NOT NULL,
            created_at          DATETIME2(3)   NOT NULL CONSTRAINT df_service_price__created_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT pk_service_price PRIMARY KEY (id),
            CONSTRAINT fk_service_price__created_by FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user(id),
            CONSTRAINT ck_service_price__service_code CHECK (service_code IN ('BOARDING_CARE_DAILY','TRAINING_SESSION')),
            CONSTRAINT ck_service_price__unit_price CHECK (unit_price > 0 AND unit_price <= 1000000000 AND unit_price = ROUND(unit_price, 0)),
            CONSTRAINT ck_service_price__currency_code CHECK (currency_code = 'VND')
        );
        CREATE INDEX ix_service_price__code_created ON dbo.service_price (service_code, created_at DESC, id DESC);
    END;

    IF OBJECT_ID(N'dbo.service_invoice', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.service_invoice (
            id                    BIGINT IDENTITY(1,1) NOT NULL,
            invoice_code          VARCHAR(30)    NOT NULL,
            horse_id              BIGINT         NOT NULL,
            owner_user_id         BIGINT         NOT NULL,
            billing_month         DATE           NOT NULL,
            period_start          DATE           NOT NULL,
            period_end            DATE           NOT NULL,
            total_amount          DECIMAL(19,4)  NOT NULL,
            currency_code         CHAR(3)        NOT NULL CONSTRAINT df_service_invoice__currency_code DEFAULT 'VND',
            status                VARCHAR(20)    NOT NULL,
            due_date              DATE           NOT NULL,
            issued_by_user_id     BIGINT         NOT NULL,
            issued_at             DATETIME2(3)   NOT NULL CONSTRAINT df_service_invoice__issued_at DEFAULT SYSUTCDATETIME(),
            paid_at               DATETIME2(3)   NULL,
            cancelled_by_user_id  BIGINT         NULL,
            cancelled_at          DATETIME2(3)   NULL,
            cancellation_reason   NVARCHAR(500)  NULL,
            created_at            DATETIME2(3)   NOT NULL CONSTRAINT df_service_invoice__created_at DEFAULT SYSUTCDATETIME(),
            updated_at            DATETIME2(3)   NULL,
            CONSTRAINT pk_service_invoice PRIMARY KEY (id),
            CONSTRAINT uq_service_invoice__invoice_code UNIQUE (invoice_code),
            CONSTRAINT fk_service_invoice__horse FOREIGN KEY (horse_id) REFERENCES dbo.horse(id),
            CONSTRAINT fk_service_invoice__owner FOREIGN KEY (owner_user_id) REFERENCES dbo.app_user(id),
            CONSTRAINT fk_service_invoice__issued_by FOREIGN KEY (issued_by_user_id) REFERENCES dbo.app_user(id),
            CONSTRAINT fk_service_invoice__cancelled_by FOREIGN KEY (cancelled_by_user_id) REFERENCES dbo.app_user(id),
            CONSTRAINT ck_service_invoice__status CHECK (status IN ('ISSUED','PAID','CANCELLED')),
            CONSTRAINT ck_service_invoice__billing_month CHECK (DAY(billing_month) = 1),
            CONSTRAINT ck_service_invoice__period CHECK (period_start >= billing_month AND period_end >= period_start
                AND period_end < DATEADD(MONTH, 1, billing_month)),
            CONSTRAINT ck_service_invoice__total_amount CHECK (total_amount > 0),
            CONSTRAINT ck_service_invoice__currency_code CHECK (currency_code = 'VND'),
            CONSTRAINT ck_service_invoice__paid CHECK (status <> 'PAID' OR paid_at IS NOT NULL),
            CONSTRAINT ck_service_invoice__cancelled CHECK (status <> 'CANCELLED'
                OR (cancelled_at IS NOT NULL AND cancelled_by_user_id IS NOT NULL AND cancellation_reason IS NOT NULL))
        );
        -- A month may be billed in parts (the current month up to today, the rest later); parts never overlap.
        -- The service checks overlap under SERIALIZABLE; this index forbids two live invoices starting the same day.
        EXEC(N'CREATE UNIQUE INDEX ux_service_invoice__horse_period_start ON dbo.service_invoice (horse_id, period_start)
            WHERE status <> ''CANCELLED''');
        CREATE INDEX ix_service_invoice__owner_month ON dbo.service_invoice (owner_user_id, billing_month DESC);
    END;

    IF OBJECT_ID(N'dbo.service_invoice_line', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.service_invoice_line (
            id            BIGINT IDENTITY(1,1) NOT NULL,
            invoice_id    BIGINT         NOT NULL,
            line_no       INT            NOT NULL,
            service_code  VARCHAR(30)    NOT NULL,
            description   NVARCHAR(200)  NOT NULL,
            quantity      INT            NOT NULL,
            unit_price    DECIMAL(19,4)  NOT NULL,
            amount        DECIMAL(19,4)  NOT NULL,
            created_at    DATETIME2(3)   NOT NULL CONSTRAINT df_service_invoice_line__created_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT pk_service_invoice_line PRIMARY KEY (id),
            CONSTRAINT uq_service_invoice_line__invoice_line UNIQUE (invoice_id, line_no),
            CONSTRAINT fk_service_invoice_line__invoice FOREIGN KEY (invoice_id) REFERENCES dbo.service_invoice(id),
            CONSTRAINT ck_service_invoice_line__service_code CHECK (service_code IN ('BOARDING_CARE_DAILY','TRAINING_SESSION')),
            CONSTRAINT ck_service_invoice_line__quantity CHECK (quantity > 0),
            CONSTRAINT ck_service_invoice_line__amount CHECK (unit_price > 0 AND amount = quantity * unit_price)
        );
    END;

    IF OBJECT_ID(N'dbo.payment_transaction', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.payment_transaction (
            id                       BIGINT IDENTITY(1,1) NOT NULL,
            invoice_id               BIGINT         NOT NULL,
            provider                 VARCHAR(20)    NOT NULL,
            txn_ref                  VARCHAR(40)    NOT NULL,
            amount                   DECIMAL(19,4)  NOT NULL,
            status                   VARCHAR(20)    NOT NULL,
            requested_by_user_id     BIGINT         NOT NULL,
            provider_create_date     CHAR(14)       NOT NULL,
            expires_at               DATETIME2(3)   NOT NULL,
            provider_transaction_no  VARCHAR(20)    NULL,
            bank_code                VARCHAR(20)    NULL,
            card_type                VARCHAR(20)    NULL,
            response_code            VARCHAR(5)     NULL,
            transaction_status       VARCHAR(5)     NULL,
            provider_pay_date        CHAR(14)       NULL,
            confirmed_via            VARCHAR(10)    NULL,
            confirmed_at             DATETIME2(3)   NULL,
            created_at               DATETIME2(3)   NOT NULL CONSTRAINT df_payment_transaction__created_at DEFAULT SYSUTCDATETIME(),
            updated_at               DATETIME2(3)   NULL,
            CONSTRAINT pk_payment_transaction PRIMARY KEY (id),
            CONSTRAINT uq_payment_transaction__txn_ref UNIQUE (txn_ref),
            CONSTRAINT fk_payment_transaction__invoice FOREIGN KEY (invoice_id) REFERENCES dbo.service_invoice(id),
            CONSTRAINT fk_payment_transaction__requested_by FOREIGN KEY (requested_by_user_id) REFERENCES dbo.app_user(id),
            CONSTRAINT ck_payment_transaction__provider CHECK (provider IN ('VNPAY')),
            -- REFUND_REQUIRED: VNPay confirmed money for an invoice that was already paid or cancelled; the Club Manager
            -- must refund it from the VNPay merchant portal. It is never counted as a payment of the invoice.
            CONSTRAINT ck_payment_transaction__status CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUND_REQUIRED')),
            CONSTRAINT ck_payment_transaction__amount CHECK (amount > 0),
            CONSTRAINT ck_payment_transaction__confirmed_via CHECK (confirmed_via IS NULL OR confirmed_via IN ('IPN','QUERYDR')),
            CONSTRAINT ck_payment_transaction__success CHECK (status <> 'SUCCESS'
                OR (confirmed_via IS NOT NULL AND confirmed_at IS NOT NULL AND provider_transaction_no IS NOT NULL))
        );
        EXEC(N'CREATE UNIQUE INDEX ux_payment_transaction__invoice_success ON dbo.payment_transaction (invoice_id)
            WHERE status = ''SUCCESS''');
        CREATE INDEX ix_payment_transaction__invoice ON dbo.payment_transaction (invoice_id, created_at DESC);
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
