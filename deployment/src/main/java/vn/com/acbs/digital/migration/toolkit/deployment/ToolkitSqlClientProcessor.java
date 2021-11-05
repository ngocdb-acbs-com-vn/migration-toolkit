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
package vn.com.acbs.digital.migration.toolkit.deployment;

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.vertx.mutiny.pgclient.PgPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.com.acbs.digital.migration.toolkit.models.Resource;
import vn.com.acbs.digital.migration.toolkit.models.ResourceLoader;
import vn.com.acbs.digital.migration.toolkit.models.VersionedMigration;
import vn.com.acbs.digital.migration.toolkit.runtime.ToolkitBuildTimeConfig;
import vn.com.acbs.digital.migration.toolkit.runtime.ToolkitRecorder;
import vn.com.acbs.digital.migration.toolkit.runtime.ToolkitRuntimeConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

public class ToolkitSqlClientProcessor {

    private static final Logger log = LoggerFactory.getLogger(ToolkitSqlClientProcessor.class);

    private static final String JAR_PROTOCOL = "jar";

    private static final String FILE_PROTOCOL = "file";

    public static String TOOLKIT_SQL_CLIENT = "toolkit-sql-client";

    public static String PG_TOOLKIT_SQL_CLIENT = "pg-toolkit-sql-client";

    ToolkitBuildTimeConfig config;

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem configureRuntimeProperties(ToolkitRecorder recorder, ToolkitRuntimeConfig runtimeConfig,
                                                     ToolkitPoolBuildItem poolBuildItem, BeanContainerBuildItem beanContainer) {
        BeanContainer container = beanContainer.getValue();
        recorder.doStartActions(poolBuildItem.getPool(), runtimeConfig, container);
        return new ServiceStartBuildItem(TOOLKIT_SQL_CLIENT);
    }

    @BuildStep
    void build(BuildProducer<FeatureBuildItem> feature) {
        feature.produce(new FeatureBuildItem(PG_TOOLKIT_SQL_CLIENT));
    }

    @BuildStep
    ServiceStartBuildItem configureRuntimeProperties(BuildProducer<ToolkitPoolBuildItem> pool) {
        pool.produce(new ToolkitPoolBuildItem(PgPool.class));
        return new ServiceStartBuildItem(PG_TOOLKIT_SQL_CLIENT);
    }

    @BuildStep
    UnremovableBeanBuildItem setup() {
        return UnremovableBeanBuildItem.beanClassNames(PgPool.class.getName());
    }

    @BuildStep
    @Record(STATIC_INIT)
    void build(BuildProducer<FeatureBuildItem> feature, ToolkitRecorder recorder,
               BuildProducer<NativeImageResourceBuildItem> resource) throws IOException, URISyntaxException {

        feature.produce(new FeatureBuildItem(TOOLKIT_SQL_CLIENT));

        // location
        String location = config.location;
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("'migration.location' is empty!");
        }

        // find migration resources
        List<Resource> resources = getMigrationFiles(location);
        if (!resources.isEmpty()) {

            // validate resources
            ResourceLoader.validateResources(resources);

            // native resource
            String[] paths = resources.stream().map(x -> x.script).toArray(String[]::new);
            resource.produce(new NativeImageResourceBuildItem(paths));

            // add the repeatable migrations to recorder
            List<Resource> repeatableMigration = resources.stream()
                    .filter(r -> r.repeatable).sorted().collect(Collectors.toList());
            ;
            if (!repeatableMigration.isEmpty()) {
                recorder.setRepeatableMigrations(repeatableMigration);
            }

            // add the versioned migrations to recorder
            List<VersionedMigration> migrations = resources.stream()
                    .filter(r -> !r.repeatable)
                    .map(VersionedMigration::new).sorted().collect(Collectors.toList());
            if (!migrations.isEmpty()) {
                recorder.setVersionedMigrations(migrations);
            }
        }
        // add imports
        List<String> afterMigrationScripts = config.testDataScripts;
        afterMigrationScripts.remove(ToolkitBuildTimeConfig.DEFAULT_TEST_DATA_SCRIPTS);
        if (!afterMigrationScripts.isEmpty()) {
            recorder.setAfterMigrationScripts(afterMigrationScripts);
            resource.produce(new NativeImageResourceBuildItem(afterMigrationScripts.toArray(new String[0])));
        }

    }



    private List<Resource> getMigrationFiles(String location) throws IOException, URISyntaxException {
        if (location == null || location.isBlank()) {
            return Collections.emptyList();
        }
        List<Resource>   result     = new ArrayList<>();
        Enumeration<URL> migrations = Thread.currentThread().getContextClassLoader().getResources(location);
        while (migrations.hasMoreElements()) {
            URL path = migrations.nextElement();
            log.info("Adding application migrations in path '{}' using protocol '{}'", path.getPath(), path.getProtocol());

            if (JAR_PROTOCOL.equals(path.getProtocol())) {
                try (final FileSystem fileSystem = initFileSystem(path.toURI())) {
                    result.addAll(getResources(location, path, JAR_PROTOCOL));
                }
            } else if (FILE_PROTOCOL.equals(path.getProtocol())) {
                result.addAll(getResources(location, path, FILE_PROTOCOL));
            } else {
                log.warn("Unsupported URL protocol '{}' for path '{}'. Migration files will not be discovered.", path.getProtocol(), path.getPath());
            }
        }
        return result;
    }

    private Set<Resource> getResources(final String location, final URL path, final String protocol) throws IOException, URISyntaxException {
        try (final Stream<Path> pathStream = Files.walk(Paths.get(path.toURI()))) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .map(it -> {
                        String   resourcePath = Paths.get(location, it.getFileName().toString()).toString();
                        Resource r            = ResourceLoader.createFrom(resourcePath);
                        byte[]   data         = loadResourceContent(protocol, it, resourcePath);
                        r.checksum = ResourceLoader.checksum(data);
                        return r;
                    })
                    .filter(r -> r.checksum > 0)
                    .peek(it -> log.debug("Discovered: " + it))
                    .collect(Collectors.toSet());
        }
    }

    private byte[] loadResourceContent(String protocol, Path it, String resourcePath) {
        if (JAR_PROTOCOL.equals(protocol)) {
            try {
                return Files.readAllBytes(it);
            }
            catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        } else {
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
                if (in != null) {
                    return in.readAllBytes();
                }
            }
            catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return null;
    }

    private FileSystem initFileSystem(final URI uri) throws IOException {
        final Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        return FileSystems.newFileSystem(uri, env);
    }

}
