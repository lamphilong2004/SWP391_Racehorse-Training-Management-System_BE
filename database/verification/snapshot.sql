-- Read-only ordered sections; bounded independent metadata grants.
-- SECTION settings
SELECT CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation')) AS collation OPTION (MAXDOP 1, LOOP JOIN);
-- SECTION tables
SELECT s.name AS [schema],t.name FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE t.is_ms_shipped=0 OPTION (MAXDOP 1, LOOP JOIN);
-- SECTION columns
SELECT OBJECT_SCHEMA_NAME(c.object_id) AS [schema],OBJECT_NAME(c.object_id) AS [table],c.column_id AS ordinal,c.name,ty.name AS [type],c.max_length,c.precision,c.scale,c.is_nullable,c.is_identity,c.is_computed,c.collation_name,CONVERT(nvarchar(100),ic.seed_value) AS identity_seed,CONVERT(nvarchar(100),ic.increment_value) AS identity_increment,cc.definition AS computed_definition,cc.is_persisted FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id LEFT JOIN sys.identity_columns ic ON ic.object_id=c.object_id AND ic.column_id=c.column_id LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE t.is_ms_shipped=0 OPTION (MAXDOP 1, LOOP JOIN);
-- SECTION keys
SELECT OBJECT_SCHEMA_NAME(k.parent_object_id) AS [schema],OBJECT_NAME(k.parent_object_id) AS [table],k.name,k.type,i.type_desc AS index_type FROM sys.key_constraints k JOIN sys.indexes i ON i.object_id=k.parent_object_id AND i.index_id=k.unique_index_id OPTION (MAXDOP 1, LOOP JOIN);
-- SECTION foreign_keys
SELECT OBJECT_SCHEMA_NAME(f.parent_object_id) AS [schema],OBJECT_NAME(f.parent_object_id) AS [table],f.name,f.delete_referential_action_desc,f.update_referential_action_desc,f.is_disabled,f.is_not_trusted,fc.constraint_column_id AS ordinal,COL_NAME(fc.parent_object_id,fc.parent_column_id) AS [column],OBJECT_SCHEMA_NAME(fc.referenced_object_id) AS target_schema,OBJECT_NAME(fc.referenced_object_id) AS target_table,COL_NAME(fc.referenced_object_id,fc.referenced_column_id) AS target_column FROM sys.foreign_keys f JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=f.object_id OPTION (MAXDOP 1, LOOP JOIN);
-- SECTION checks
SELECT OBJECT_SCHEMA_NAME(parent_object_id) AS [schema],OBJECT_NAME(parent_object_id) AS [table],name,definition,is_disabled,is_not_trusted FROM sys.check_constraints OPTION (MAXDOP 1, LOOP JOIN);
-- SECTION defaults
SELECT OBJECT_SCHEMA_NAME(parent_object_id) AS [schema],OBJECT_NAME(parent_object_id) AS [table],name,COL_NAME(parent_object_id,parent_column_id) AS [column],definition FROM sys.default_constraints OPTION (MAXDOP 1, LOOP JOIN);
-- SECTION indexes
SELECT OBJECT_SCHEMA_NAME(i.object_id) AS [schema],OBJECT_NAME(i.object_id) AS [table],i.name,i.type_desc,i.is_unique,i.is_primary_key,i.is_unique_constraint,i.is_disabled,i.has_filter,i.filter_definition,ic.index_column_id AS ordinal,ic.key_ordinal,ic.is_descending_key,ic.is_included_column,COL_NAME(ic.object_id,ic.column_id) AS [column] FROM sys.indexes i JOIN sys.tables t ON t.object_id=i.object_id JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE t.is_ms_shipped=0 AND i.is_hypothetical=0 OPTION (MAXDOP 1, LOOP JOIN);
