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

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

import java.util.List;

@ConfigRoot(name = "migration-toolkit", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class ToolkitBuildTimeConfig {

    private static final String DEFAULT_LOCATION = "db/migration/";

    public static final String DEFAULT_TEST_DATA_SCRIPTS = "-no-value-";

    /**
     * Gets the default build time configuration
     *
     * @return the build time default configuration
     */
    public static ToolkitBuildTimeConfig defaultConfig() {
        return new ToolkitBuildTimeConfig();
    }

    /**
     * The location of SQL scripts. All included sql log files in this file are scanned and add to the projects.
     */
    @ConfigItem(defaultValue = DEFAULT_LOCATION)
    public String location;

    /**
     * List of SQL scripts which will be run as test data after migration.
     */
    @ConfigItem(defaultValue = DEFAULT_TEST_DATA_SCRIPTS)
    public List<String> testDataScripts;
}
