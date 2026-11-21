-- Issue #63: MoMo ATM sandbox. Additive; previous migrations and application data remain intact.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment_transaction')
               AND name = N'ck_payment_transaction__provider' AND definition NOT LIKE N'%MOMO%')
    BEGIN
        ALTER TABLE dbo.payment_transaction DROP CONSTRAINT ck_payment_transaction__provider;
        ALTER TABLE dbo.payment_transaction WITH CHECK ADD CONSTRAINT ck_payment_transaction__provider
            CHECK (provider IN ('VNPAY','VIETQR','MOMO'));
    END;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.payment_transaction')
               AND name = N'ck_payment_transaction__confirmed_via' AND definition NOT LIKE N'%MOMO_QUERY%')
    BEGIN
        ALTER TABLE dbo.payment_transaction DROP CONSTRAINT ck_payment_transaction__confirmed_via;
        ALTER TABLE dbo.payment_transaction WITH CHECK ADD CONSTRAINT ck_payment_transaction__confirmed_via
            CHECK (confirmed_via IS NULL OR confirmed_via IN ('IPN','QUERYDR','RETURN','OWNER','MOMO_IPN','MOMO_QUERY'));
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
