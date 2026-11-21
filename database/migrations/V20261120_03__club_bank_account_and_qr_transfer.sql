-- DEC-016 follow-up (2026-11-20, Project Leader): the Horse Owner pays an invoice by scanning a VietQR code that carries
-- the club's own bank account, the invoice total and the invoice code, then presses "Thanh toán". The payment is recorded
-- as declared by the owner (provider VIETQR, confirmed_via OWNER); no bank confirms it. VNPay sandbox stays available.
-- Additive: one new table (receiving account, kept as history), widened provider / confirmed_via checks.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.club_payment_account', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.club_payment_account (
            id                  BIGINT IDENTITY(1,1) NOT NULL,
            bank_bin            VARCHAR(10)    NOT NULL,
            bank_name           NVARCHAR(100)  NOT NULL,
            account_no          VARCHAR(19)    NOT NULL,
            account_name        NVARCHAR(100)  NOT NULL,
            created_by_user_id  BIGINT         NOT NULL,
            created_at          DATETIME2(3)   NOT NULL CONSTRAINT df_club_payment_account__created_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT pk_club_payment_account PRIMARY KEY (id),
            CONSTRAINT fk_club_payment_account__created_by FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user(id),
            CONSTRAINT ck_club_payment_account__bank_bin CHECK (bank_bin NOT LIKE '%[^0-9]%' AND LEN(bank_bin) BETWEEN 6 AND 8),
            CONSTRAINT ck_club_payment_account__account_no CHECK (account_no NOT LIKE '%[^0-9A-Za-z]%' AND LEN(account_no) BETWEEN 4 AND 19)
        );
    END;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_payment_transaction__provider'
               AND parent_object_id = OBJECT_ID(N'dbo.payment_transaction') AND definition NOT LIKE N'%VIETQR%')
    BEGIN
        ALTER TABLE dbo.payment_transaction DROP CONSTRAINT ck_payment_transaction__provider;
        ALTER TABLE dbo.payment_transaction ADD CONSTRAINT ck_payment_transaction__provider
            CHECK (provider IN ('VNPAY','VIETQR'));
    END;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_payment_transaction__confirmed_via'
               AND parent_object_id = OBJECT_ID(N'dbo.payment_transaction') AND definition NOT LIKE N'%OWNER%')
    BEGIN
        ALTER TABLE dbo.payment_transaction DROP CONSTRAINT ck_payment_transaction__confirmed_via;
        ALTER TABLE dbo.payment_transaction ADD CONSTRAINT ck_payment_transaction__confirmed_via
            CHECK (confirmed_via IS NULL OR confirmed_via IN ('IPN','QUERYDR','RETURN','OWNER'));
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
