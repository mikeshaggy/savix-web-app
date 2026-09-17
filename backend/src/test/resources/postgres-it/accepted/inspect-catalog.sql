-- Read-only schema metadata; no application data or sequence advancement.
-- psql -X -qAt -v ON_ERROR_STOP=1 -v schema=public -f inspect-catalog.sql
BEGIN READ ONLY;
SET LOCAL search_path TO :"schema", pg_catalog;
WITH entries AS (
    SELECT jsonb_build_object('section', 'columns', 'schema', n.nspname, 'table', c.relname,
        'column', a.attname, 'type', format_type(a.atttypid, a.atttypmod),
        'not_null', a.attnotnull, 'default', pg_get_expr(d.adbin, d.adrelid),
        'identity', a.attidentity, 'generated', a.attgenerated) AS entry
    FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    WHERE n.nspname = :'schema' AND c.relkind = 'r' AND a.attnum > 0 AND NOT a.attisdropped
    UNION ALL
    SELECT jsonb_build_object('section', 'constraints', 'table', c.relname, 'name', con.conname,
        'type', con.contype, 'validated', con.convalidated, 'definition', pg_get_constraintdef(con.oid))
    FROM pg_constraint con JOIN pg_class c ON c.oid = con.conrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = :'schema'
    UNION ALL
    SELECT jsonb_build_object('section', 'indexes', 'table', c.relname, 'name', idx.relname,
        'unique', i.indisunique, 'valid', i.indisvalid, 'definition', pg_get_indexdef(i.indexrelid))
    FROM pg_index i JOIN pg_class c ON c.oid = i.indrelid
    JOIN pg_class idx ON idx.oid = i.indexrelid JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = :'schema'
)
SELECT jsonb_pretty(jsonb_agg(entry ORDER BY entry::text)) FROM entries;
COMMIT;
