package vn.com.acbs.digital.migration.toolkit.database;

import io.vertx.mutiny.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.com.acbs.digital.migration.toolkit.models.*;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Database {

    private static Logger log = LoggerFactory.getLogger(Database.class);

    protected final Pool client;

    protected final String table;

    private String currentUser;

    public Database(final String table, final Pool client) {
        this.client = client;
        this.table = table;
    }

    protected abstract void cleanSchema();

    protected abstract String historyTableSql();

    protected abstract Boolean tryLock();

    protected abstract void unlock();

    protected abstract boolean checkMigrationTable();

    protected abstract String getCurrentUser();

    protected abstract String getInsertMigrationSQL();

    public void testData(List<String> testDataScripts) {
        if (testDataScripts == null || testDataScripts.isEmpty()) {
            log.warn("Test data scripts is empty!");
            return;
        }
        log.info("Execute test data scripts");
        for (String resource : testDataScripts) {
            String sql = ResourceLoader.loadResource(resource);
            if (sql == null || sql.isEmpty()) {
                log.warn("Skip empty test data scripts. Resource: " + resource);
            } else {
                Transaction tx = null;
                try {
                    // create transaction
                    tx = client.getConnectionAndAwait().beginAndAwait();

                    // execute SQL script
                    log.info("Script {}", resource);
                    queryAndAwait(client, sql);

                    // commit
                    tx.commitAndAwait();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (tx != null) {
                        tx.rollbackAndAwait();
                    }
                    throw new IllegalStateException("Error execute test data scripts! Resource:" + resource, ex);
                }
            }
        }
    }

    public void doClean() {
        log.info("Clean database");
        cleanSchema();
    }

    public void doMigration(List<VersionedMigration> versionedMigrations, List<Resource> repeatableMigrations) {
        if (versionedMigrations == null || versionedMigrations.isEmpty()) {
            return;
        }

        // check table
        boolean te = checkMigrationTable();
        if (te) {
            // load latest migration
            Migration latest = lastVersionedMigration();
            if (latest != null) {
                // filter resources
                Version ver = Version.of(latest.version);
                boolean exists = versionedMigrations.stream().anyMatch(ver::isLessThan);
                // check the migration
                if (!exists) {
                    return;
                }
            }
        }

        try {
            log.info("Migrate database");

            // create lock
            lock();

            currentUser = getCurrentUser();

            Migration latest = null;
            long id = 0;

            // check migration table
            if (!te && !checkMigrationTable()) {
                queryAndAwait(historyTableSql());
            } else {
                // load last migration
                id = 1 + lastId();
                latest = lastVersionedMigration();
            }

            // filter resources
            List<Migration> migrations = createMigrations(versionedMigrations, latest, id);

            // start migration
            if (!migrations.isEmpty()) {
                latest = migrations(migrations);

                // repeatable migration
                if (repeatableMigrations != null && !repeatableMigrations.isEmpty()) {
                    Map<String, Migration> rms = getAllRepeatableMigration();
                    List<Migration> executeRepeatableMigrations = createRepeatableMigrations(latest, repeatableMigrations, rms);
                    if (!executeRepeatableMigrations.isEmpty()) {
                        migrations(executeRepeatableMigrations);
                    } else {
                        log.debug("No repeatable migration to run!");
                    }
                }

            } else {
                log.warn("No versioned migration to run!");
            }

            log.info("Database version: {}", latest != null ? latest.version : null);
        } finally {
            // release lock
            try {
                unlock();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to release database lock", e);
           }
        }
    }

    protected Migration migrations(List<Migration> migrations) {
        Migration result = null;
        // start migration
        for (Migration migration : migrations) {
            migration(migration);
            result = migration;
        }
        return result;
    }

    protected void migration(Migration migration) {
        String sql = ResourceLoader.loadResource(migration.script);
        if (sql == null || sql.isEmpty()) {
            log.warn("Skip empty migration resources " + migration.script);
        } else {
            Transaction tx = null;
            try {
                // begin transaction
                tx = client.getConnectionAndAwait().beginAndAwait();

                // start migration
                log.info("Script {}", migration.script);
                long start = System.currentTimeMillis();
                queryAndAwait(client, sql);
                long time = System.currentTimeMillis() - start;

                // insert or update executed migration
                if (migration.exists) {
                    updateMigration(client, migration, time);
                } else {
                    insertMigration(client, migration, time);
                }

                // commit
                tx.commitAndAwait();
            } catch (Exception ex) {
                ex.printStackTrace();
                if (tx != null) {
                    tx.rollbackAndAwait();
                }
                throw new IllegalStateException("Error execute migration!", ex);
            }
        }
    }

    protected void updateMigration(SqlClient tx, Migration migration, Long time) {
        preparedQueryAndAwait(tx,
                "UPDATE " + table + " SET checksum = $1, execution_time = $2, installed_by = $3 WHERE id=$4",
                Tuple.of(migration.checksum, time, currentUser, migration.id));
    }


    protected void insertMigration(SqlClient tx, Migration migration, Long time) {
        preparedQueryAndAwait(tx, getInsertMigrationSQL(), Tuple.tuple(Arrays.asList(
                migration.id, migration.version, migration.description, migration.type,
                migration.script, migration.checksum, time, true, currentUser
                ))
        );
    }

    protected void lock() {
        int retries = 0;
        while (!tryLock()) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted lock", e);
            }

            if (++retries >= 50) {
                throw new IllegalStateException("Number of retries exceeded while attempting to acquire lock");
            }
        }
    }

    protected Map<String, Migration> getAllRepeatableMigration() {
        RowIterator<Row> it = queryAndAwait("SELECT * FROM " + table + " WHERE version IS NULL").iterator();
        Map<String, Migration> result = new HashMap<>();
        while (it.hasNext()) {
            Migration m = map(it.next());
            result.put(m.description, m);
        }
        return result;
    }

    protected long lastId() {
        RowIterator<Row> it = queryAndAwait("SELECT id FROM " + table + " ORDER BY id DESC LIMIT 1").iterator();
        if (it.hasNext()) {
            return it.next().getLong("id");
        }
        return -1;
    }

    public Migration lastVersionedMigration() {
        RowIterator<Row> it = queryAndAwait("SELECT * FROM " + table + " WHERE version IS NOT NULL ORDER BY id DESC LIMIT 1").iterator();
        if (it.hasNext()) {
            return map(it.next());
        }
        return null;
    }

    public static Migration map(Row row) {
        Migration m = new Migration();
        m.exists = true;
        m.id = row.getLong("id");
        m.version = row.getString("version");
        m.description = row.getString("description");
        m.type = row.getString("type");
        m.script = row.getString("script");
        m.checksum = row.getLong("checksum");
        m.installedBy = row.getString("installed_by");
        m.installedOn = row.getLocalDateTime("installed_on");
        m.time = row.getLong("execution_time");
        m.success = row.getBoolean("success");
        return m;
    }

    protected List<Migration> createMigrations(List<VersionedMigration> resources, Migration latest, long id) {
        List<VersionedMigration> versions = resources;
        if (latest != null) {
            Version ver = Version.of(latest.version);
            versions = resources.stream().filter(ver::isLessThan).collect(Collectors.toList());
        }

        List<Migration> result = new ArrayList<>();
        for (VersionedMigration m : versions) {
            result.add(create(m.resource, id++));
        }
        return result;
    }

    protected List<Migration> createRepeatableMigrations(Migration latest, List<Resource> repeatableMigrations, Map<String, Migration> rms) {
        long id = latest.id + 1;
        List<Migration> result = new ArrayList<>();
        for (Resource rm : repeatableMigrations) {
            Migration m = rms.get(rm.description);
            if (m != null) {
                if (!m.checksum.equals(rm.checksum)) {
                    result.add(m);
                }
            } else {
                m = create(rm, id++);
                result.add(m);
            }
        }
        return result;
    }

    protected Migration create(Resource resource, long id) {
        Migration r = new Migration();
        r.exists = false;
        r.id = id;
        r.version = resource.version;
        r.description = resource.description;
        r.type = "SQL";
        r.script = resource.script;
        r.checksum = resource.checksum;
        return r;
    }

    protected void preparedQueryAndAwait(SqlClient tx, String sql, io.vertx.mutiny.sqlclient.Tuple arguments) {
        log.debug("SQL:\n" + sql);
        tx.preparedQuery(sql).executeAndAwait(arguments);
    }

    protected RowSet<Row> queryAndAwait(String sql) {
        return queryAndAwait(client, sql);
    }

    private static RowSet<Row> queryAndAwait(SqlClient client, String sql) {
        log.debug("SQL:\n" + sql);
        return client.query(sql).executeAndAwait();
    }
}
