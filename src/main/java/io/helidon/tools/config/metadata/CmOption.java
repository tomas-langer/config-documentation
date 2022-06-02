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

public class CmOption {
    private String key;
    private String description;
    private String method;
    private String type = "string";
    private String defaultValue;
    private boolean required = false;
    private boolean experimental = false;
    private boolean deprecated = false;
    private boolean provider = false;
    private boolean merge = false;
    private Kind kind = Kind.VALUE;
    private String refType;
    private List<CmAllowedValue> allowedValues;

    public List<CmAllowedValue> getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(List<CmAllowedValue> allowedValues) {
        this.allowedValues = allowedValues;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isExperimental() {
        return experimental;
    }

    public CmOption setExperimental(boolean experimental) {
        this.experimental = experimental;
        return this;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public CmOption setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
        return this;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public boolean isProvider() {
        return provider;
    }

    public void setProvider(boolean provider) {
        this.provider = provider;
    }

    public boolean isMerge() {
        return merge;
    }

    public void setMerge(boolean merge) {
        this.merge = merge;
    }

    public enum Kind {
        /**
         * Option is a single value (leaf node).
         * Example: server port
         */
        VALUE,
        /**
         * Option is a list of values (either primitive, String or object nodes).
         * Example: cipher suite in SSL, server sockets
         */
        LIST,
        /**
         * Option is a map of strings to primitive type or String.
         * Example: tags in tracing, CDI configuration
         */
        MAP
    }
}
