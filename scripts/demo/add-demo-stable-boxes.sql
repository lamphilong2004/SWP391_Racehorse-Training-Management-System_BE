-- Demo stable boxes C-01 .. C-99 (reference data; the API has no endpoint to create boxes).
-- Inserts only missing codes and repairs only the yard name of demo boxes C-xx; never deletes. ASCII-only on purpose (NCHAR). Run on your own local database:
--   sqlcmd -S localhost -E -d <your database> -i scripts/demo/add-demo-stable-boxes.sql
SET XACT_ABORT ON; SET NOCOUNT ON;
BEGIN TRANSACTION;
DECLARE @boxes TABLE (box_code varchar(20) NOT NULL, yard nvarchar(100) NOT NULL);
DECLARE @i int = 1;
WHILE @i <= 99
BEGIN
    INSERT @boxes (box_code, yard) VALUES (CONCAT('C-', RIGHT(CONCAT('0', @i), 2)), N'Khu chu' + NCHAR(7891) + N'ng C'); -- NCHAR keeps the name correct whatever code page sqlcmd reads the file with
    SET @i += 1;
END;
INSERT dbo.stable_box (box_code, yard)
SELECT b.box_code, b.yard FROM @boxes b
WHERE NOT EXISTS (SELECT 1 FROM dbo.stable_box s WITH (UPDLOCK, HOLDLOCK) WHERE s.box_code = b.box_code);
SELECT @@ROWCOUNT AS inserted;
-- Repair demo boxes inserted by older copies of this file through a non-UTF-8 code page (garbled yard name).
UPDATE dbo.stable_box SET yard = N'Khu chu' + NCHAR(7891) + N'ng C'
WHERE box_code LIKE 'C-[0-9][0-9]' AND yard <> N'Khu chu' + NCHAR(7891) + N'ng C';
SELECT @@ROWCOUNT AS yard_name_repaired;
COMMIT TRANSACTION;
SELECT COUNT(*) AS total_boxes FROM dbo.stable_box;
