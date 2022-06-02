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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.URLTemplateSource;
import org.eclipse.yasson.YassonConfig;

class ConfigDocumentation {
    private static final Map<String, String> TYPE_MAPPING;
    private static final Jsonb JSON_B = JsonbBuilder.create(new YassonConfig().withFailOnUnknownProperties(true));

    static {
        Map<String, String> typeMapping = new HashMap<>();
        typeMapping.put("java.lang.String", "string");
        typeMapping.put("java.lang.Integer", "int");
        typeMapping.put("java.lang.Boolean", "boolean");
        typeMapping.put("java.lang.Long", "long");
        typeMapping.put("java.lang.Character", "char");
        typeMapping.put("java.lang.Float", "float");
        typeMapping.put("java.lang.Double", "double");
        TYPE_MAPPING = Map.copyOf(typeMapping);
    }

    private final Path path;
    private final Predicate<String> modulePredicate;

    ConfigDocumentation(Path path, Predicate<String> modulePredicate) {
        this.path = path;
        this.modulePredicate = modulePredicate;
    }

    void process() throws Exception {
        Handlebars handlebars = new Handlebars();
        URL resource = ConfigDocumentation.class.getResource("/type-docs.adoc.hbs");
        if (resource == null) {
            throw new IllegalStateException("Could not locate required handlebars template on classpath: type-docs.adoc.hbs");
        }
        Template template = handlebars.compile(new URLTemplateSource("type-docs.adoc.hbs", resource));
        Enumeration<URL> files = ConfigDocumentation.class.getClassLoader().getResources("META-INF/helidon/config-metadata.json");

        List<CmModule> allModules = new LinkedList<>();

        while (files.hasMoreElements()) {
            URL url = files.nextElement();
            try (InputStream is = url.openStream()) {
                CmModule[] cmModules = JSON_B.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), CmModule[].class);
                allModules.addAll(Arrays.asList(cmModules));
            }
        }

        Map<String, CmType> configuredTypes = new HashMap<>();

        for (CmModule module : allModules) {
            for (CmType type : module.getTypes()) {
                configuredTypes.put(type.getType(), type);
            }
        }

        resolveInheritance(configuredTypes);

        // we now need to resolve all inheritances
        for (CmModule module : allModules) {
            if (modulePredicate.test(module.getModule())) {
                moduleDocs(template, configuredTypes, path, module);
            }
        }
    }

    private static void moduleDocs(Template template, Map<String, CmType> configuredTypes, Path modulePath, CmModule module)
            throws IOException {
        System.out.println("Documenting module " + module.getModule());
        // each type will have its own, such as:
        // docs/io.helidon.common.configurable/LruCache.adoc
        for (CmType type : module.getTypes()) {
            Path typePath = modulePath.resolve(type.getType() + ".adoc");
            // just overwrite
            Files.writeString(typePath,
                              typeFile(template, type),
                              StandardOpenOption.TRUNCATE_EXISTING,
                              StandardOpenOption.CREATE);
            // now write the relevant json
        }
    }

    private static CharSequence typeFile(Template template, CmType type) throws IOException {
        boolean hasRequired = false;
        boolean hasOptional = false;
        for (CmOption option : type.getOptions()) {
            if (option.isRequired()) {
                hasRequired = true;
            } else {
                hasOptional = true;
            }
            option.setRefType(mapType(option));
        }

        Map<String, Object> context = Map.of("year", ZonedDateTime.now().getYear(),
                                             "hasRequired", hasRequired,
                                             "hasOptional", hasOptional,
                                             "type", type);
        return template.apply(context);
    }

    private static String mapType(CmOption option) {
        String type = option.getType();
        String mapped = TYPE_MAPPING.get(type);
        CmOption.Kind kind = option.getKind();

        String displayType = displayType(kind, mapped == null ? type : mapped);

        if (mapped == null) {
            if (option.getAllowedValues() != null && !option.getAllowedValues().isEmpty()) {
                return mapAllowedValues(option, kind, displayType);
            }
            if (option.isProvider()) {
                return byKind(kind, type) + " (service provider interface)";
            }
            if (type.startsWith("io.helidon")) {
                if (type.equals("io.helidon.config.Config")) {
                    return "Map&lt;string, string&gt; (documented for specific cases)";
                }
                return "link:" + type + ".adoc[" + displayType + "]" + (option.isMerge() ? " (merged)" : "");
            }
        }
        return displayType;
    }

    private static String displayType(CmOption.Kind kind, String type) {
        int lastIndex = type.lastIndexOf('.');
        if (lastIndex == -1) {
            return byKind(kind, type);
        }
        String name = type.substring(lastIndex + 1);
        if ("Builder".equals(name)) {
            String base = type.substring(0, lastIndex);
            lastIndex = base.lastIndexOf('.');
            if (lastIndex == -1) {
                // this is a pure Builder class, need to show package to distinguish
                return byKind(kind, type);
            } else {
                return byKind(kind, base.substring(lastIndex + 1) + ".Builder");
            }
        } else {
            return byKind(kind, name);
        }
    }

    private static String byKind(CmOption.Kind kind, String type) {
        // no dots
        switch (kind) {
        case LIST:
            return type + "[&#93;";
        case MAP:
            return "Map&lt;string, " + type + "&gt;";
        case VALUE:
        default:
            return type;
        }
    }

    private static String mapAllowedValues(CmOption option, CmOption.Kind kind, String displayType) {
        List<CmAllowedValue> values = option.getAllowedValues();

        return displayType
                + " (" + values.stream().map(CmAllowedValue::getValue).collect(Collectors.joining(", ")) + ")";
    }

    private void resolveInheritance(Map<String, CmType> configuredTypes) {
        Map<String, CmType> resolved = new HashMap<>();
        List<CmType> remaining = new ArrayList<>(configuredTypes.values());

        boolean didResolve = true;

        while (didResolve) {
            didResolve = false;
            for (int i = 0; i < remaining.size(); i++) {
                CmType next = remaining.get(i);
                if (next.getInherits() == null) {
                    resolved.put(next.getType(), next);
                    didResolve = true;
                    remaining.remove(i);
                    break;
                } else {
                    boolean allExist = true;
                    for (String inherit : next.getInherits()) {
                        if (!resolved.containsKey(inherit)) {
                            allExist = false;
                        }
                    }
                    if (allExist) {
                        resolveInheritance(resolved, next);
                        resolved.put(next.getType(), next);
                        didResolve = true;
                        remaining.remove(i);
                        break;
                    }
                }
            }
        }

        if (remaining.size() > 0) {
            System.err.println("There are types with inheritance that is not on classpath: ");
            for (CmType cmType : remaining) {
                System.err.println("Type " + cmType.getType() + ", inherits: " + cmType.getInherits());
            }
        }
    }

    private void resolveInheritance(Map<String, CmType> resolved, CmType next) {
        for (String inherit : next.getInherits()) {
            CmType cmType = resolved.get(inherit);
            List<CmOption> options = new ArrayList<>(next.getOptions());
            options.addAll(cmType.getOptions());
            next.setOptions(options);
            ;
        }
        next.setInherits(null);
    }
}
