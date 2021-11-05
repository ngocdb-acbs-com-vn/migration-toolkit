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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VersionTest {

    @Test
    public void compareTest() {
        List<String> result = Arrays.asList("1.0.0","10.0", "10.0.1","89.99.9", "90", "99.9.0", "100");
        List<String> versions = Arrays.asList("99.9.0", "100", "89.99.9", "10.0.1", "10.0", "1.0.0", "90");
        List<String> sorted = versions.stream()
                .map(Version::new)
                .sorted()
                .map(v -> v.value)
                .collect(Collectors.toList());

        Assertions.assertEquals(result, sorted);
    }
}
