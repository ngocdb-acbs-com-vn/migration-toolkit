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
package vn.com.acbs.digital.migration.toolkit.models;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceLoaderTest {

    private static final Logger log = LoggerFactory.getLogger(ResourceLoaderTest.class);

    private static final String[] VALUES_OK = {
            "V1__ASD.sql",
            "V1.1.1.1.1.__ASD.sql",
            "asd/test/data/V3.4__ASD.sql",
            "/migration/test/data/V99999999.1__ASD.sql"
    };

    private static final String[] VALUES_ERROR = {
            "",
            null,
            "V__ASD.sql",
            "R1.0.0__Test1.sql"
    };

    @Test
    public void createFromTest() {
        for (String item : VALUES_OK) {
            log.info("Item: {}", item);
            Resource resource = ResourceLoader.createFrom(item);
            log.info("Resource: {}", resource);
            Assertions.assertNotNull(resource);
        }
        for (String item : VALUES_ERROR) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                log.info("Wrong item: {}", item);
                try {
                    ResourceLoader.createFrom(item);
                } catch (Exception ex) {
                    log.info("Error: {}", ex.getMessage());
                    log.info("Error: {}", ex.getCause() != null ? ex.getCause().getMessage() : "");
                    throw ex;
                }
            });
        }
    }
}
