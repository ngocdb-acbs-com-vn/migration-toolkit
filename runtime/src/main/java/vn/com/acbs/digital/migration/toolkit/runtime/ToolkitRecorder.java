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
package vn.com.acbs.digital.migration.toolkit.runtime;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.mutiny.sqlclient.Pool;
import vn.com.acbs.digital.migration.toolkit.Toolkit;
import vn.com.acbs.digital.migration.toolkit.ToolkitConfig;
import vn.com.acbs.digital.migration.toolkit.models.Resource;
import vn.com.acbs.digital.migration.toolkit.models.VersionedMigration;

import javax.enterprise.inject.Default;
import java.util.List;

@Recorder
public class ToolkitRecorder {

    public static List<VersionedMigration> versionedMigrations;

    public static List<Resource> repeatableMigrations;

    public static List<String> afterMigrationScripts;

    public void setRepeatableMigrations(List<Resource> repeatableMigrations) {
        ToolkitRecorder.repeatableMigrations = repeatableMigrations;
    }

    public void setAfterMigrationScripts(List<String> afterMigrationScripts) {
        ToolkitRecorder.afterMigrationScripts = afterMigrationScripts;
    }

    public void setVersionedMigrations(List<VersionedMigration> versionedMigration) {
        ToolkitRecorder.versionedMigrations = versionedMigration;
    }

    /**
     * Do start actions
     *
     * @param config the runtime configuration
     */
    public void doStartActions(Class<? extends Pool> pool,ToolkitRuntimeConfig config, BeanContainer container) {
        try {
            Pool          client = container.instance(pool, Default.Literal.INSTANCE);
            ToolkitConfig toolkitConfig = ToolkitConfig.builder()
                    .table(config.historyTable)
                    .afterMigrationScripts(afterMigrationScripts)
                    .versionedMigrations(versionedMigrations)
                    .repeatableMigrations(repeatableMigrations)
                    .build();
            Toolkit toolkit = new Toolkit(client, toolkitConfig);
            if (config.cleanAtStart) {
                toolkit.clean();
            }
            if (config.migrateAtStart) {
                toolkit.migration();
            }
            if (config.testData) {
                toolkit.testData();
            }
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

}
