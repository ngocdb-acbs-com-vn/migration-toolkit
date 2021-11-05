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

public class ResourceTest {

    @Test
    public void compareResourceTest() {
        List<String> desc = Arrays.asList("2", "1234", null, "ASD");
        List<String> result = Arrays.asList(desc.get(2), desc.get(1), desc.get(0), desc.get(3));

        List<Resource> resources = desc.stream()
                .map(this::create)
                .collect(Collectors.toList());

        List<String> sorted = resources.stream()
                .sorted()
                .map(x -> x.description)
                .collect(Collectors.toList());
        Assertions.assertEquals(result, sorted);
    }

    private Resource create(String description) {
        Resource r = new Resource();
        r.description = description;
        return r;
    }
}
