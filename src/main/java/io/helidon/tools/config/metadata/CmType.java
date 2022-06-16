/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tools.config.metadata;

import java.util.List;

public class CmType {

    private String type;
    private String annotatedType;
    private List<CmOption> options;
    private String description;
    private String prefix;
    private boolean standalone;
    private List<String> inherits;
    private List<String> producers;

    private List<String> provides;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean isStandalone() {
        return standalone;
    }

    public void setStandalone(boolean standalone) {
        this.standalone = standalone;
    }

    public List<String> getInherits() {
        return inherits;
    }

    public void setInherits(List<String> inherits) {
        this.inherits = inherits;
    }

    public List<String> getProducers() {
        return producers;
    }

    public void setProducers(List<String> producers) {
        this.producers = producers;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAnnotatedType() {
        return annotatedType;
    }

    public void setAnnotatedType(String annotatedType) {
        this.annotatedType = annotatedType;
    }

    public List<CmOption> getOptions() {
        return options;
    }

    public void setOptions(List<CmOption> options) {
        this.options = options;
    }

    public List<String> getProvides() {
        return provides;
    }

    public void setProvides(List<String> provides) {
        this.provides = provides;
    }

    public boolean hasProvides() {
        return provides != null && !provides.isEmpty();
    }

    @Override
    public String toString() {
        return getType();
    }
}
