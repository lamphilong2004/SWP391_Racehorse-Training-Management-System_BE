-- Demo stable boxes C-01 .. C-20 (reference data; the API has no endpoint to create boxes).
-- Inserts only missing codes; never updates or deletes. Run on your own local database:
--   sqlcmd -S localhost -E -d <your database> -i scripts/demo/add-demo-stable-boxes.sql
SET XACT_ABORT ON; SET NOCOUNT ON;
BEGIN TRANSACTION;
DECLARE @boxes TABLE (box_code varchar(20) NOT NULL, yard nvarchar(100) NOT NULL);
DECLARE @i int = 1;
WHILE @i <= 20
BEGIN
    INSERT @boxes (box_code, yard) VALUES (CONCAT('C-', RIGHT(CONCAT('0', @i), 2)), N'Khu chuồng C');
    SET @i += 1;
END;
INSERT dbo.stable_box (box_code, yard)
SELECT b.box_code, b.yard FROM @boxes b
WHERE NOT EXISTS (SELECT 1 FROM dbo.stable_box s WITH (UPDLOCK, HOLDLOCK) WHERE s.box_code = b.box_code);
SELECT @@ROWCOUNT AS inserted;
COMMIT TRANSACTION;
SELECT COUNT(*) AS total_boxes FROM dbo.stable_box;
