/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.FileNormalizer;

import javax.annotation.Nullable;

public class DefaultInputFilePropertySpec extends AbstractFilePropertySpec implements InputFilePropertySpec {
    private final boolean skipWhenEmpty;
    private final PropertyValue value;

    public DefaultInputFilePropertySpec(String propertyName, Class<? extends FileNormalizer> normalizer, FileCollection files, PropertyValue value, boolean skipWhenEmpty) {
        super(propertyName, normalizer, files);
        this.value = value;
        this.skipWhenEmpty = skipWhenEmpty;
    }

    @Override
    public boolean isSkipWhenEmpty() {
        return skipWhenEmpty;
    }

    @Override
    @Nullable
    public Object getValue() {
        return value.call();
    }
}
