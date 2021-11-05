/*
 * Copyright 2020 lorislab.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vn.com.acbs.digital.migration.toolkit;

import io.vertx.mutiny.sqlclient.Pool;

import vn.com.acbs.digital.migration.toolkit.database.Database;
import vn.com.acbs.digital.migration.toolkit.database.PostgresDatabase;
import vn.com.acbs.digital.migration.toolkit.models.Migration;

public class Toolkit {

    public static final String HISTORY_TABLE = "toolkit_migration";

    private final Database database;

    private final ToolkitConfig config;

    public Toolkit(final Pool client, final ToolkitConfig config) {
        if (client == null) {
            throw new NullPointerException("Pool client is null!");
        }
        if (config == null) {
            throw new NullPointerException("Configuration is null!");
        }
        this.config = config;
        String clazz = client.getClass().getName();
        if (clazz.startsWith(PostgresDatabase.POOL)) {
            database = new PostgresDatabase(config.getHistoryTable(), client);
        } else {
            database = null;
        }
        if (database == null) {
            throw new IllegalStateException("Not supported pool client. Class: " + clazz);
        }
    }

    public ToolkitConfig getConfig() {
        return config;
    }

    public void testData() {
        database.testData(config.getTestDataScripts());
    }

    public void clean() {
        database.doClean();
    }

    public void migration() {
        database.doMigration(config.getVersionedMigrations(), config.getRepeatableMigrations());
    }

    public String version() {
        Migration migration = database.lastVersionedMigration();
        if (migration != null) {
            return migration.version;
        }
        return null;
    }

}
