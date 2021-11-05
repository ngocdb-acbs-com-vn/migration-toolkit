package vn.com.acbs.digital.migration.toolkit.database;

import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowIterator;

public class PostgresDatabase extends Database {

    public static final String POOL = "io.vertx.mutiny.pgclient.PgPool";

    // b + a + r + n + d + b
    private static final long LOCK_NUM = + (0x62L << 48) + (0x61L << 32) + (0x72L << 24) + (0x6E << 16) + (0x64 << 8) + 0x62;

    private final long lockNum;

    public PostgresDatabase(String table, Pool client) {
        super(table, client);
        this.lockNum = LOCK_NUM + table.hashCode();
    }

    @Override
    protected String getCurrentUser() {
        RowIterator<Row> it = queryAndAwait("SELECT CURRENT_USER").iterator();
        return it.hasNext() ? it.next().getString(0) : "";
    }

    protected boolean checkMigrationTable() {
        RowIterator<Row> it = queryAndAwait(checkIfTableExistsQuery( table)).iterator();
        int tmp = it.hasNext() ? it.next().getInteger(0) : 0;
        return tmp > 0;
    }

    public static String checkIfTableExistsQuery(String table) {
        return  "SELECT count(to_regclass('" + table + "'))";
    }

    @Override
    protected Boolean tryLock() {
        RowIterator<Row> it = queryAndAwait("SELECT pg_try_advisory_lock(" + lockNum + ")").iterator();
        return it.hasNext() ? it.next().getBoolean("pg_try_advisory_lock") : false;
    }

    @Override
    protected void unlock() {
        queryAndAwait("SELECT pg_advisory_unlock(" + lockNum + ")");
    }

    @Override
    protected String getInsertMigrationSQL() {
        return "INSERT INTO " + table +
                " (id,version,description,type,script,checksum,execution_time,success,installed_by)" +
                " VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)";
    }

    @Override
    protected String historyTableSql() {
        return "CREATE TABLE " + table + " (\n" +
                "    \"id\" INT NOT NULL,\n" +
                "    \"version\" VARCHAR(50),\n" +
                "    \"description\" VARCHAR(200) NOT NULL,\n" +
                "    \"type\" VARCHAR(20) NOT NULL,\n" +
                "    \"script\" VARCHAR(1000) NOT NULL,\n" +
                "    \"checksum\" BIGINT,\n" +
                "    \"installed_by\" TEXT NOT NULL,\n" +
                "    \"installed_on\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "    \"execution_time\" BIGINT NOT NULL,\n" +
                "    \"success\" BOOLEAN NOT NULL\n" +
                ");\n" +
                "ALTER TABLE " + table + " ADD CONSTRAINT \"" + table + "_pk\" PRIMARY KEY (\"id\");\n" +
                "CREATE INDEX \"" + table + "_s_idx\" ON " + table + " (\"success\");";
    }

    private String sc(String value) {
        return value;
    }

    public void cleanSchema() {

        // drop all materialized views
        for (Row row : queryAndAwait("SELECT relname FROM pg_catalog.pg_class c " +
                " JOIN pg_namespace n ON n.oid = c.relnamespace " +
                " WHERE c.relkind = 'm' AND n.nspname = current_schema()")) {
            queryAndAwait("DROP MATERIALIZED VIEW IF EXISTS " + sc(row.getString(0)) + " CASCADE");
        }

        // drop all statement views
        for (Row row : queryAndAwait("SELECT relname FROM pg_catalog.pg_class c " +
                " JOIN pg_namespace n ON n.oid = c.relnamespace" +
                " LEFT JOIN pg_depend dep ON dep.objid = c.oid AND dep.deptype = 'e' " +
                " WHERE c.relkind = 'v' AND  n.nspname = current_schema() AND  dep.objid IS NULL")) {
            queryAndAwait("DROP VIEW IF EXISTS " + sc(row.getString(0)) + " CASCADE");
        }

        // drop all tables
        for (Row row : queryAndAwait("SELECT t.table_name FROM information_schema.tables t" +
                " LEFT JOIN pg_depend dep ON dep.objid = (quote_ident(t.table_schema)||'.'||quote_ident(t.table_name))::regclass::oid AND dep.deptype = 'e'" +
                " WHERE t.table_schema=current_schema() AND table_type='BASE TABLE' AND dep.objid IS NULL" +
                " AND NOT (SELECT EXISTS (SELECT inhrelid FROM pg_catalog.pg_inherits" +
                " WHERE inhrelid = (quote_ident(t.table_schema)||'.'||quote_ident(t.table_name))::regclass::oid))")) {
            queryAndAwait("DROP TABLE " + sc(row.getString(0)) + " CASCADE");
        }

        // drop all statements for base types and created user types
        for (Row row : queryAndAwait("SELECT typname, typcategory FROM pg_catalog.pg_type t LEFT JOIN pg_depend dep ON dep.objid = t.oid AND dep.deptype = 'e'" +
                " WHERE (t.typrelid = 0 OR (SELECT c.relkind = 'c' FROM pg_catalog.pg_class c WHERE c.oid = t.typrelid))" +
                " AND NOT EXISTS(SELECT 1 FROM pg_catalog.pg_type el WHERE el.oid = t.typelem AND el.typarray = t.oid)" +
                " AND t.typnamespace IN (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = current_schema())" +
                " AND dep.objid IS NULL AND t.typtype != 'd'")) {

            String typename = row.getString(0);
            queryAndAwait("DROP TYPE IF EXISTS  " + sc(typename) + " CASCADE");
            String t = row.getString(1);

            // Only recreate Pseudo-types (P) and User-defined types (U)
            if ("P".equals(t) || "U".equals(t)) {
                queryAndAwait("CREATE TYPE " + sc(typename) + " CASCADE");
            }
        }

        // dropping all routines in this schema
        for (Row row : queryAndAwait("SELECT proname, oidvectortypes(proargtypes) AS args," +
                " CASE WHEN pg_proc.prokind='p' THEN 'PROCEDURE'" +
                "   WHEN pg_proc.prokind='a' THEN 'AGGREGATE'" +
                "   ELSE 'FUNCTION'" +
                " END as type" +
                " FROM pg_proc INNER JOIN pg_namespace ns ON (pg_proc.pronamespace = ns.oid)\n" +
                " LEFT JOIN pg_depend dep ON dep.objid = pg_proc.oid AND dep.deptype = 'e'\n" +
                " WHERE ns.nspname = current_schema() AND dep.objid IS NULL")) {
            queryAndAwait("DROP " + row.getString(2) + " IF EXISTS " + sc(row.getString(0))
                    + "(" + row.getString(1) + ")  CASCADE"
            );
        }

        // dropping the enums in this schema
        for (Row row : queryAndAwait("SELECT t.typname FROM pg_catalog.pg_type t INNER JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace" +
                " WHERE n.nspname = current_schema() AND t.typtype = 'e'")) {
            queryAndAwait("DROP TYPE  " + sc(row.getString(0)));
        }

        // dropping the domains in this schema
        for (Row row : queryAndAwait("SELECT t.typname as domain_name FROM pg_catalog.pg_type t " +
                " LEFT JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace" +
                " LEFT JOIN pg_depend dep ON dep.objid = t.oid AND dep.deptype = 'e'" +
                " WHERE t.typtype = 'd'  AND n.nspname = current_schema()  AND dep.objid IS NULL")) {
            queryAndAwait("DROP DOMAIN  " + sc(row.getString(0)));
        }

        // dropping the sequences in this schema
        for (Row row : queryAndAwait("SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema=current_schema()")) {
            queryAndAwait("DROP TYPE " + sc(row.getString(0)) + " CASCADE");
        }

        // drop all statements for base types
        for (Row row : queryAndAwait("SELECT typname, typcategory FROM pg_catalog.pg_type t LEFT JOIN pg_depend dep ON dep.objid = t.oid AND dep.deptype = 'e'" +
                " WHERE (t.typrelid = 0 OR (SELECT c.relkind = 'c' FROM pg_catalog.pg_class c WHERE c.oid = t.typrelid))" +
                " AND NOT EXISTS(SELECT 1 FROM pg_catalog.pg_type el WHERE el.oid = t.typelem AND el.typarray = t.oid)" +
                " AND t.typnamespace IN (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = current_schema())" +
                " AND dep.objid IS NULL AND t.typtype != 'd'")) {
            queryAndAwait("DROP TYPE IF EXISTS " + sc(row.getString(0)) + " CASCADE");
        }
    }
}
