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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

public class ResourceLoader {

    public static final String RESOURCE_SUFFIX = ".sql";

    public static final String PREFIX_VERSION_MIG = "V";

    public static final String PREFIX_REPEATABLE_MIG = "R";

    public static void validateResources(List<Resource> resources) {
        Set<String> version = new HashSet<>();
        for (Resource r : resources) {
            if (!r.repeatable && !version.add(r.version)) {
                throw new IllegalStateException("Found more than one migration with version " + r.version);
            }
        }
    }

    public static Resource createFrom(String item) {
        if (item == null || item.isEmpty()) {
            throw new IllegalArgumentException("The migration item is null!");
        }
        int i2 = item.lastIndexOf(RESOURCE_SUFFIX);
        if (i2 == -1) {
            throw new IllegalArgumentException("The migration item does not ends with '" + RESOURCE_SUFFIX + "'");
        }
        int i1 = item.lastIndexOf("/");

        try {
            Resource result = new Resource();
            result.script = item;

            // remove RESOURCE_SUFFIX
            String name = item.substring(i1 + 1, i2);

            // prefix
            String prefix = name.substring(0, 1);
            if (PREFIX_VERSION_MIG.equals(prefix)) {
                result.repeatable = false;
            } else if (PREFIX_REPEATABLE_MIG.equals(prefix)) {
                result.repeatable = true;
            } else {
                throw new IllegalArgumentException("Wrong prefix of the migration script. Values: [V, R]. Found: " + prefix);
            }

            // remove prefix
            name = name.substring(1);

            // split to 0: version , 1: description
            String[] ver_desc = name.split("__");
            if (ver_desc.length != 2) {
                throw new IllegalArgumentException("Wrong part for the version and description of the migration script.");
            }
            result.description = ver_desc[1].replaceAll("_", " ");
            result.version = ver_desc[0];
            if (result.repeatable) {
                if (result.version != null && !result.version.isBlank()) {
                    throw new IllegalArgumentException("Version is not supported for repeatable migration!");
                }
                result.version = null;
            } else {
                if (result.version == null || result.version.isEmpty()) {
                    throw new IllegalArgumentException("Version is null or empty for versioned migration!");
                }
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Wrong name of migration script. ['V{ver}__{desc}.sql'|'R__{desc}.sql']. Script: " + item, ex);
        }
    }

    public static long checksum(byte[] value) {
        if (value == null || value.length <= 0) {
            return 0;
        }
        CRC32 crc = new CRC32();
        crc.update(value);
        return crc.getValue();
    }

    public static byte[] loadResourceContent(String path) {
        try {
            String tmp = path;
            if (!tmp.startsWith("/")) {
                tmp = "/" + tmp;
            }
            try (InputStream in = createResourceStream(tmp)) {
                if (in != null) {
                    return in.readAllBytes();
                }
                return null;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error read the migration resource " + path);
        }
    }

    private static InputStream createResourceStream(String path) {
        InputStream in = ResourceLoader.class.getResourceAsStream(path);
        if (in == null) {
            in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        }
        return in;
    }

    public static String loadResource(String script) {
        byte[] data = loadResourceContent(script);
        if (data == null || data.length <= 0) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
