-- Local reference catalog for a member's NEW local database only (no management API exists for these rows).
-- Idempotent: inserts missing stable boxes and skills; never updates or deletes existing rows.
-- Display names are Vietnamese (DEC-011). A skill whose earlier English seed name already exists is not inserted again.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF DB_NAME() <> N'__DATABASE__'
        THROW 51020, 'Reference seed target database does not match the requested local database.', 1;

    IF CAST(SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS nvarchar(128)) <> N'__LOCAL_MACHINE__'
        THROW 51021, 'Reference seed target SQL Server is not on the local machine.', 1;

    DECLARE @boxes TABLE (box_code varchar(20) NOT NULL, yard nvarchar(100) NOT NULL);
    INSERT @boxes (box_code, yard) VALUES
        ('A-01', N'Khu chuồng A'), ('A-02', N'Khu chuồng A'), ('A-03', N'Khu chuồng A'), ('A-04', N'Khu chuồng A'),
        ('A-05', N'Khu chuồng A'), ('A-06', N'Khu chuồng A'), ('B-01', N'Khu chuồng B'), ('B-02', N'Khu chuồng B'),
        ('B-03', N'Khu chuồng B'), ('B-04', N'Khu chuồng B'), ('B-05', N'Khu chuồng B'), ('B-06', N'Khu chuồng B');

    INSERT dbo.stable_box (box_code, yard)
    SELECT b.box_code, b.yard
    FROM @boxes b
    WHERE NOT EXISTS (SELECT 1 FROM dbo.stable_box s WITH (UPDLOCK, HOLDLOCK) WHERE s.box_code = b.box_code);

    DECLARE @skills TABLE (name nvarchar(100) NOT NULL, legacy_name nvarchar(100) NOT NULL, kind varchar(30) NOT NULL);
    INSERT @skills (name, legacy_name, kind) VALUES
        (N'Rèn thể lực', N'Conditioning', 'TRAINER_SPECIALIZATION'),
        (N'Phát triển tốc độ', N'Speed development', 'TRAINER_SPECIALIZATION'),
        (N'Sức bền', N'Endurance', 'TRAINER_SPECIALIZATION'),
        (N'Phục hồi chức năng', N'Rehabilitation', 'TRAINER_SPECIALIZATION'),
        (N'Tập trên đường bằng', N'Flat work', 'RIDER_SKILL'),
        (N'Phi nước đại', N'Gallop', 'RIDER_SKILL'),
        (N'Huấn luyện ngựa non', N'Young horse handling', 'RIDER_SKILL');

    INSERT dbo.skill (name, kind)
    SELECT k.name, k.kind
    FROM @skills k
    WHERE NOT EXISTS (SELECT 1 FROM dbo.skill s WITH (UPDLOCK, HOLDLOCK)
                      WHERE s.kind = k.kind AND s.name IN (k.name, k.legacy_name));

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
