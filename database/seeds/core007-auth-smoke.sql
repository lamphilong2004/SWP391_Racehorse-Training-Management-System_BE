SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF DB_NAME() <> N'__DATABASE__'
        THROW 51010, 'Seed target database does not match the requested local test database.', 1;

    IF CAST(SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS nvarchar(128)) <> N'__LOCAL_MACHINE__'
        THROW 51011, 'Seed target SQL Server is not on the local machine.', 1;

    IF EXISTS (
        SELECT 1
        FROM dbo.app_user
        WHERE email IN (
            N'core007.club.manager@example.test',
            N'core007.head.trainer@example.test',
            N'core007.trainer@example.test',
            N'core007.work.rider@example.test',
            N'core007.veterinarian@example.test',
            N'core007.groom@example.test',
            N'core007.horse.owner@example.test')
           OR user_code IN (
            'C7-CORE007-CM01',
            'C7-CORE007-HT01',
            'C7-CORE007-TR01',
            'C7-CORE007-WR01',
            'C7-CORE007-VE01',
            'C7-CORE007-GR01',
            'C7-CORE007-HO01')
    )
        THROW 51012, 'One or more CORE-007 smoke users already exist; refusing to overwrite them.', 1;

    INSERT INTO dbo.role (role_code, display_name)
    SELECT roles.role_code, roles.role_code
    FROM (VALUES
        ('CLUB_MANAGER'),
        ('HEAD_TRAINER'),
        ('TRAINER'),
        ('WORK_RIDER'),
        ('VETERINARIAN'),
        ('GROOM'),
        ('HORSE_OWNER')
    ) AS roles(role_code)
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.role existing WHERE existing.role_code = roles.role_code);

    INSERT INTO dbo.app_user (user_code, full_name, email, password_hash, account_status)
    VALUES
        ('C7-CORE007-CM01', N'CORE-007 Local Club Manager', N'core007.club.manager@example.test', '__HASH_CLUB_MANAGER__', 'ACTIVE'),
        ('C7-CORE007-HT01', N'CORE-007 Local Head Trainer', N'core007.head.trainer@example.test', '__HASH_HEAD_TRAINER__', 'ACTIVE'),
        ('C7-CORE007-TR01', N'CORE-007 Local Trainer', N'core007.trainer@example.test', '__HASH_TRAINER__', 'ACTIVE'),
        ('C7-CORE007-WR01', N'CORE-007 Local Work Rider', N'core007.work.rider@example.test', '__HASH_WORK_RIDER__', 'ACTIVE'),
        ('C7-CORE007-VE01', N'CORE-007 Local Veterinarian', N'core007.veterinarian@example.test', '__HASH_VETERINARIAN__', 'ACTIVE'),
        ('C7-CORE007-GR01', N'CORE-007 Local Groom', N'core007.groom@example.test', '__HASH_GROOM__', 'ACTIVE'),
        ('C7-CORE007-HO01', N'CORE-007 Local Horse Owner', N'core007.horse.owner@example.test', '__HASH_HORSE_OWNER__', 'ACTIVE');

    INSERT INTO dbo.user_role (user_id, role_id, assigned_by_user_id, assigned_at)
    SELECT account.id, role.id, NULL, SYSUTCDATETIME()
    FROM (VALUES
        (N'core007.club.manager@example.test', 'CLUB_MANAGER'),
        (N'core007.head.trainer@example.test', 'HEAD_TRAINER'),
        (N'core007.trainer@example.test', 'TRAINER'),
        (N'core007.work.rider@example.test', 'WORK_RIDER'),
        (N'core007.veterinarian@example.test', 'VETERINARIAN'),
        (N'core007.groom@example.test', 'GROOM'),
        (N'core007.horse.owner@example.test', 'HORSE_OWNER')
    ) AS seed(email, role_code)
    JOIN dbo.app_user account ON account.email = seed.email
    JOIN dbo.role role ON role.role_code = seed.role_code;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT account.email, role.role_code, account.account_status
FROM dbo.app_user account
JOIN dbo.user_role assignment ON assignment.user_id = account.id
JOIN dbo.role role ON role.id = assignment.role_id
WHERE account.email LIKE N'core007.%@example.test'
ORDER BY role.role_code;
