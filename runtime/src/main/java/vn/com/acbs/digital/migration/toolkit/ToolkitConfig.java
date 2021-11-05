package vn.com.acbs.digital.migration.toolkit;


import vn.com.acbs.digital.migration.toolkit.models.Resource;
import vn.com.acbs.digital.migration.toolkit.models.VersionedMigration;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ToolkitConfig {

    private List<VersionedMigration> versionedMigrations = Collections.emptyList();

    private List<Resource> repeatableMigrations = Collections.emptyList();

    private List<String> testDataScripts = Collections.emptyList();

    private String historyTable;

    public String getHistoryTable() {
        return historyTable;
    }

    public List<Resource> getRepeatableMigrations() {
        return repeatableMigrations;
    }

    public List<VersionedMigration> getVersionedMigrations() {
        return versionedMigrations;
    }

    public List<String> getTestDataScripts() {
        return testDataScripts;
    }

    public static ToolkitConfigBuilder builder() {
        return new ToolkitConfigBuilder();
    }

    public static class ToolkitConfigBuilder {

        private ToolkitConfig config = new ToolkitConfig();

        public ToolkitConfigBuilder table(String table) {
            config.historyTable = table;
            return this;
        }

        public ToolkitConfigBuilder versionedMigrations(List<VersionedMigration> resources) {
            if (resources != null) {
                config.versionedMigrations = resources;
            }
            return this;
        }

        public ToolkitConfigBuilder repeatableMigrations(List<Resource> resources) {
            if (resources != null) {
                config.repeatableMigrations = resources;
            }
            return this;
        }

        public ToolkitConfigBuilder afterMigrationScripts(List<String> resources) {
            if (resources != null) {
                config.testDataScripts = resources;
            }
            return this;
        }

        public ToolkitConfigBuilder sorted() {
            if (config.versionedMigrations != null && !config.versionedMigrations.isEmpty()) {
                config.versionedMigrations = config.versionedMigrations.stream().sorted().collect(Collectors.toList());
            }
            if (config.repeatableMigrations != null && !config.repeatableMigrations.isEmpty()) {
                config.repeatableMigrations = config.repeatableMigrations.stream().sorted().collect(Collectors.toList());
            }
            return this;
        }

        public ToolkitConfig build() {
            return config;
        }
    }
}
