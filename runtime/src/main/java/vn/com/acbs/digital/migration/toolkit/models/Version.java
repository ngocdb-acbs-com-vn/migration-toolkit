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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Version implements Comparable<Version> {

    public String value;

    public List<Integer> numbers;

    public Version() {
    }

    public Version(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The migration version value is null!");
        }
        this.value = value;
        this.numbers = Arrays.stream(value.split("\\.")).map(Integer::valueOf).collect(Collectors.toList());
    }

    public static Version of(String value) {
        return new Version(value);
    }

    @Override
    public int hashCode() {
        if (numbers == null) {
            return 0;
        }
        return numbers.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        Version version = (Version) obj;
        return compareTo(version) == 0;
    }

    public boolean isBiggerThan(Version version) {
        return compareTo(version) > 0;
    }

    public boolean isLessThan(Version version) {
        return compareTo(version) < 0;
    }

    @Override
    public int compareTo(Version version) {
        if (version == null) {
            return 1;
        }
        int size = Math.max(numbers.size(), version.numbers.size());
        for (int i = 0; i < size; i++) {
            int result = get(numbers, i).compareTo(get(version.numbers, i));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static Integer get(List<Integer> items, int i) {
        return i < items.size() ? items.get(i) : Integer.MIN_VALUE;
    }

    @Override
    public String toString() {
        return value;
    }
}
