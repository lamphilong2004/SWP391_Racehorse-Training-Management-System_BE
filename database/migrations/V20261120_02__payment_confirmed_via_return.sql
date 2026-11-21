-- DEC-016 follow-up (2026-11-20): a VNPay result may also be confirmed from the signed return URL when neither the IPN
-- (needs a public HTTPS URL) nor querydr (sandbox answered 99 "Unexpected error") is available. The return parameters
-- are HMAC-SHA512 signed by VNPay with the merchant secret, and amount, terminal and status are checked by the server.
-- Widens ck_payment_transaction__confirmed_via only. No business row is inserted, updated or deleted.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_payment_transaction__confirmed_via'
               AND parent_object_id = OBJECT_ID(N'dbo.payment_transaction')
               AND definition NOT LIKE N'%RETURN%')
    BEGIN
        ALTER TABLE dbo.payment_transaction DROP CONSTRAINT ck_payment_transaction__confirmed_via;
        ALTER TABLE dbo.payment_transaction ADD CONSTRAINT ck_payment_transaction__confirmed_via
            CHECK (confirmed_via IS NULL OR confirmed_via IN ('IPN','QUERYDR','RETURN'));
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
