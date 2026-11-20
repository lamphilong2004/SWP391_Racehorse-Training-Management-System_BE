-- DEC-016: stable boxes are no longer added freely. Every box is a cell of a yard (stable_yard rows x columns), created by
-- migration V20261120_01 (yards A, B, C: 2 x 10 = 60 boxes) or by the Club Manager on "Sơ đồ chuồng" (POST /api/stable-yards,
-- at most 6 yards of 4 x 10). This script only reports the layout; it never inserts, updates or deletes a row.
--   sqlcmd -S localhost -E -d <your database> -i scripts/demo/add-demo-stable-boxes.sql
SET NOCOUNT ON;
IF OBJECT_ID(N'dbo.stable_yard', N'U') IS NULL
BEGIN
    PRINT 'Apply database/migrations/V20261120_01__stable_layout_and_service_billing.sql first.';
    RETURN;
END;
SELECT y.yard_code, y.name, y.row_count, y.column_count, y.row_count * y.column_count AS capacity,
       SUM(CASE WHEN b.box_status = 'ACTIVE' AND a.id IS NULL THEN 1 ELSE 0 END) AS free_boxes,
       SUM(CASE WHEN a.id IS NOT NULL THEN 1 ELSE 0 END) AS occupied_boxes,
       SUM(CASE WHEN b.box_status = 'MAINTENANCE' THEN 1 ELSE 0 END) AS maintenance_boxes
FROM dbo.stable_yard y
LEFT JOIN dbo.stable_box b ON b.stable_yard_id = y.id
LEFT JOIN dbo.horse_stable_assignment a ON a.stable_box_id = b.id AND a.effective_to IS NULL
GROUP BY y.yard_code, y.name, y.row_count, y.column_count
ORDER BY y.yard_code;
