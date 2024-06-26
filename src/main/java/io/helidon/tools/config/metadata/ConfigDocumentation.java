/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.URLTemplateSource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.yasson.YassonConfig;

class ConfigDocumentation {
    private static final Pattern MODULE_PATTERN = Pattern.compile("(.*?)(\\.spi)?\\.([a-zA-Z0-9]*?)");
    private static final Pattern COPYRIGHT_LINE_PATTERN = Pattern.compile(".*Copyright \\(c\\) (.*) Oracle and/or its affiliates.");
    private static final Jsonb JSON_B = JsonbBuilder.create(new YassonConfig().withFailOnUnknownProperties(true));
    private static final Map<String, String> TYPE_MAPPING;

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
    private final String relativePath;
    private final Predicate<String> modulePredicate;

    ConfigDocumentation(Path path, String relativePath, Predicate<String> modulePredicate) {
        this.path = path;
        this.relativePath = relativePath;
        this.modulePredicate = modulePredicate;
    }

    static String titleFromFileName(String fileName) {
        String title = fileName;
        // string .adoc
        if (title.endsWith(".adoc")) {
            title = title.substring(0, title.length() - 5);
        }
        if (title.startsWith("io_helidon_")) {
            title = title.substring("io_helidon_".length());
            int i = title.lastIndexOf('_');
            if (i != -1) {
                String simpleName = title.substring(i + 1);
                String thePackage = title.substring(0, i);
                title = simpleName + " (" + thePackage.replace('_', '.') + ")";
            }
        }
        return title;
    }

    // translate HTML to asciidoc
    static String translateHtml(String text) {
        String result = text;
        // <p>
        result = result.replaceAll("\n\\s*<p>", "\n");
        result = result.replaceAll("\\s*<p>", "\n");
        result = result.replaceAll("</p>", "");
        // <ul><nl><li>
        result = result.replaceAll("\\s*</li>\\s*", "");
        result = result.replaceAll("\\s*</ul>\\s*", "\n\n");
        result = result.replaceAll("\\s*</nl>\\s*", "\n\n");
        result = result.replaceAll("\n\\s*<ul>\\s*", "\n");
        result = result.replaceAll("\\s*<ul>\\s*", "\n");
        result = result.replaceAll("\n\\s*<nl>\\s*", "\n");
        result = result.replaceAll("\\s*<nl>\\s*", "\n");
        result = result.replaceAll("<li>\\s*", "\n- ");
        // also fix javadoc issues
        // {@value}
        result = result.replaceAll("\\{@value\\s+#?(.*?)}", "`$1`");
        // {@link}
        result = result.replaceAll("\\{@link\\s+#?(.*?)}", "`$1`");
        // table - keep as is, just a passthrough
        StringBuilder theBuilder = new StringBuilder();
        int lastIndex = 0;
        while (true) {
            int index = result.indexOf("<table", lastIndex);
            if (index == -1) {
                // add the rest of the string
                theBuilder.append(result.substring(lastIndex));
                break;
            }
            int endIndex = result.indexOf("</table>", index);
            theBuilder.append(result.substring(lastIndex, index));
            theBuilder.append("\n++++\n");
            theBuilder.append(result.substring(index, endIndex + 8));
            theBuilder.append("\n++++\n");
            lastIndex = endIndex + 8;
        }
        return theBuilder.toString();
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

        // map of annotated types to documentation
        Map<String, CmType> configuredTypes = new HashMap<>();

        for (CmModule module : allModules) {
            for (CmType type : module.getTypes()) {
                configuredTypes.put(type.getAnnotatedType(), type);
            }
        }

        // translate HTML in description
        translateHtml(configuredTypes);
        // add all inherited options to each type
        resolveInheritance(configuredTypes);
        // add all options from merged types as direct options to each type
        resolveMerges(configuredTypes);
        // resolve type reference (for javadocs)
        resolveTypeReference(configuredTypes);
        // add titles (remove io.helidon from package or similar)
        addTitle(configuredTypes);

        List<String> generatedFiles = new LinkedList<>();
        for (CmModule module : allModules) {
            if (modulePredicate.test(module.getModule())) {
                // document module that is included by the predicate
                moduleDocs(configuredTypes, template, path, relativePath, module, generatedFiles);
            }
        }

        // sort alphabetically by page title
        generatedFiles.sort((a, b) -> titleFromFileName(a).compareTo(titleFromFileName(b)));
        // print out the list of files to be added to sitegen.yaml
        System.out.println("Update config/config_reference with the following values:");

        for (String generatedFile : generatedFiles) {
            System.out.println("- xref:{rootdir}/config/" + generatedFile + "[" + titleFromFileName(generatedFile) + "]");
        }
    }

    private static String title(String typeName) {
        String title = typeName;
        if (title.startsWith("io.helidon.")) {
            title = title.substring("io.helidon.".length());
            int i = title.lastIndexOf('.');
            if (i != -1) {
                String simpleName = title.substring(i + 1);
                String thePackage = title.substring(0, i);
                title = simpleName + " (" + thePackage + ")";
            }
        }
        return title;
    }

    private static void moduleDocs(Map<String, CmType> configuredTypes,
                                   Template template,
                                   Path modulePath,
                                   String relativePath,
                                   CmModule module,
                                   List<String> generatedFiles) throws IOException {
        Function<String, Boolean> exists = type -> {
            // 1: check if part of this processing
            if (configuredTypes.containsKey(type)) {
                return true;
            }
            // 2: check if exists in target directory
            String path = type.replace('.', '_') + ".adoc";
            return Files.exists(modulePath.resolve(path));
        };
        System.out.println("Documenting module " + module.getModule());
        // each type will have its own, such as:
        // docs/io.helidon.common.configurable/LruCache.adoc
        for (CmType type : module.getTypes()) {
            generateType(generatedFiles, configuredTypes, template, modulePath, type, relativePath, exists);
        }
    }

    private static void generateType(List<String> generatedFiles,
                                     Map<String, CmType> configuredTypes,
                                     Template template,
                                     Path modulePath,
                                     CmType type,
                                     String relativePath,
                                     Function<String, Boolean> exists) throws IOException {
        sortOptions(type);

        String fileName = fileName(type.getType());
        Path typePath = modulePath.resolve(fileName);

        boolean sameContent = false;
        if (Files.exists(typePath)) {
            // check if maybe the file content is not modified
            CharSequence current = typeFile(configuredTypes,
                                            template,
                                            type,
                                            relativePath,
                                            exists,
                                            currentCopyrightYears(typePath));
            if (sameContent(typePath, current)) {
                sameContent = true;
            }
        }

        CharSequence fileContent = typeFile(configuredTypes,
                                            template,
                                            type,
                                            relativePath,
                                            exists,
                                            newCopyrightYears(typePath));




        generatedFiles.add(fileName);
        if (!sameContent) {
            // Write the target type
            Files.writeString(typePath,
                              fileContent,
                              StandardOpenOption.TRUNCATE_EXISTING,
                              StandardOpenOption.CREATE);
        }

        if (!type.getAnnotatedType().startsWith(type.getType())) {
            // generate two docs, just to make sure we do not have a conflict
            // example: Zipkin and Jaeger generate target type io.opentracing.Tracer, yet we need separate documents
            fileName = fileName(type.getAnnotatedType());
            Path annotatedTypePath = modulePath.resolve(fileName);
            generatedFiles.add(fileName);
            if (!sameContent) {
                // Write the annotated type (needed for Jaeger & Zipkin that produce the same target)
                Files.writeString(annotatedTypePath,
                                  fileContent,
                                  StandardOpenOption.TRUNCATE_EXISTING,
                                  StandardOpenOption.CREATE);
            }
        }
    }

    private static boolean sameContent(Path typePath, CharSequence current) {
        try {
            return Files.readString(typePath).equals(current.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String fileName(String typeName) {
        return typeName.replace('.', '_') + ".adoc";
    }

    private static void sortOptions(CmType type) {
        List<CmOption> options = new ArrayList<>(type.getOptions());
        options.sort(Comparator.comparing(CmOption::getKey));
        type.setOptions(options);
    }

    private static CharSequence typeFile(Map<String, CmType> configuredTypes,
                                         Template template,
                                         CmType type,
                                         String relativePath,
                                         Function<String, Boolean> exists,
                                         String copyrightYears) throws IOException {
        boolean hasRequired = false;
        boolean hasOptional = false;
        for (CmOption option : type.getOptions()) {
            if (option.isRequired()) {
                hasRequired = true;
            } else {
                hasOptional = true;
            }
            option.setRefType(mapType(configuredTypes, option, relativePath, exists));
        }

        Map<String, Object> context = Map.of("year", copyrightYears,
                                             "hasRequired", hasRequired,
                                             "hasOptional", hasOptional,
                                             "type", type);
        return template.apply(context);
    }

    private static String newCopyrightYears(Path typePath) {
        String currentYear = String.valueOf(ZonedDateTime.now().getYear());

        if (Files.exists(typePath)) {
            // get current copyright year
            String copyrightYears = currentCopyrightYears(typePath);
            if (copyrightYears == null) {
                return currentYear;
            }
            if (copyrightYears.endsWith(currentYear)) {
                return copyrightYears;
            }
            int index = copyrightYears.indexOf(',');
            if (index == -1) {
                return copyrightYears + ", " + currentYear;
            }
            return copyrightYears.substring(0, index) + ", " + currentYear;
        }
        return currentYear;
    }

    private static String currentCopyrightYears(Path typePath) {
        try (var lines = Files.lines(typePath)) {
            return lines.flatMap(line -> {
                        Matcher matcher = COPYRIGHT_LINE_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            return Stream.of(matcher.group(1));
                        }
                        return Stream.empty();
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String mapType(Map<String, CmType> configuredTypes,
                                  CmOption option,
                                  String relativePath,
                                  Function<String, Boolean> exists) {
        String type = option.getType();
        String mapped = TYPE_MAPPING.get(type);
        CmOption.Kind kind = option.getKind();

        String displayType = displayType(kind, mapped == null ? type : mapped);

        if (mapped == null) {
            if (option.getAllowedValues() != null && !option.getAllowedValues().isEmpty()) {
                return mapAllowedValues(option, kind, displayType);
            }
            if (option.isProvider()) {
                String providerType = option.getProviderType();
                providerType = (providerType == null) ? type : providerType;
                StringBuilder typeString = new StringBuilder(byKind(kind, type));
                typeString.append(" (service provider interface)");

                // let's try to locate available service implementations on classpath
                List<CmType> providers = findProviders(configuredTypes, providerType);
                if (!providers.isEmpty()) {
                    typeString.append("\n\nSuch as:\n\n");
                    for (CmType provider : providers) {
                        String linkText = displayType(CmOption.Kind.VALUE, provider.getType());
                        if (provider.getPrefix() != null) {
                            linkText = provider.getPrefix() + " (" + linkText + ")";
                        }
                        typeString.append(" - ")
                                .append(toLink(relativePath, provider.getType(), linkText, exists));
                        typeString.append("\n");
                    }
                    typeString.append("\n");
                }
                return typeString.toString();
            }
            return toLink(relativePath, type, displayType, exists);
        }
        return displayType;
    }

    private static String toLink(String relativePath, String type, String displayType, Function<String, Boolean> exists) {
        if (type.startsWith("io.helidon")) {
            if (type.equals("io.helidon.config.Config") || type.equals("io.helidon.common.config.Config")) {
                return "Map&lt;string, string&gt; (documented for specific cases)";
            }
            // make sure the file exists
            if (exists.apply(type)) {
                return "xref:" + relativePath + type.replace('.', '_') + ".adoc[" + displayType + "]";
            }
        }
        return displayType;
    }

    private static List<CmType> findProviders(Map<String, CmType> configuredTypes, String providerInterface) {
        return configuredTypes.values()
                .stream()
                .filter(it -> it.getProvides() != null)
                .filter(it -> it.getProvides().contains(providerInterface))
                .toList();
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

    private void addTitle(Map<String, CmType> configuredTypes) {
        for (CmType value : configuredTypes.values()) {
            value.setTitle(title(value.getType()));
        }
    }

    private void resolveTypeReference(Map<String, CmType> configuredTypes) {
        for (CmType value : configuredTypes.values()) {
            value.setTypeReference(resolveTypeReference(value.getType()));
        }
    }

    private String resolveTypeReference(String type) {
        if (type.startsWith("io.helidon")) {
            // our type
            return resolveModuleFromType(type);
        } else {
            // no reference
            return type;
        }
    }

    private String resolveModuleFromType(String type) {
        Matcher m = MODULE_PATTERN.matcher(type);
        if (m.matches()) {
            String moduleName = m.group(1);
            return "link:{javadoc-base-url}/" + moduleName + "/" + toJavadocLink(type) + "[" + type + "]";
        }
        return type;
    }

    private String toJavadocLink(String type) {
        return type.replace('.', '/') + ".html";
    }

    private void translateHtml(Map<String, CmType> configuredTypes) {
        for (CmType value : configuredTypes.values()) {
            value.getOptions().forEach(this::translateHtml);
        }
    }

    private void translateHtml(CmOption option) {
        String description = option.getDescription();
        option.setDescription(translateHtml(description));
    }

    private void resolveMerges(Map<String, CmType> configuredTypes) {
        List<CmType> remaining = new ArrayList<>(configuredTypes.values());
        Map<String, CmType> resolved = new HashMap<>();
        boolean shouldExit = false;
        while (!shouldExit) {
            shouldExit = true;

            for (int i = 0; i < remaining.size(); i++) {
                CmType next = remaining.get(i);
                boolean isResolved = true;
                List<CmOption> options = next.getOptions();
                for (int j = 0; j < options.size(); j++) {
                    CmOption option = options.get(j);
                    if (option.isMerge()) {
                        isResolved = false;
                        if (resolved.containsKey(option.getType())) {
                            options.remove(j);
                            options.addAll(resolved.get(option.getType()).getOptions());
                            shouldExit = false;
                            break;
                        }
                    }
                }

                if (isResolved) {
                    resolved.put(next.getType(), next);
                    remaining.remove(i);
                    shouldExit = false;
                    break;
                }
            }
        }

        if (remaining.size() > 0) {
            System.err.println("There are types with merged type that is not on classpath: ");
            for (CmType cmType : remaining) {
                for (CmOption option : cmType.getOptions()) {
                    if (option.isMerge()) {
                        System.err.println("Option " + option.getKey() + ", merges: " + option.getType() + " in "
                                                   + cmType.getAnnotatedType());
                    }
                }

            }
        }
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
                    resolved.put(next.getAnnotatedType(), next);
                    didResolve = true;
                    remaining.remove(i);
                    break;
                } else {
                    boolean allExist = true;
                    for (String inherit : next.getInherits()) {
                        if (!resolved.containsKey(inherit)) {
                            allExist = false;
                            break;
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
        // Allow option info on subclasses or impls of interfaces to override option info from higher.
        Map<String, CmOption> options = new HashMap<>();

        List<String> inherits = next.getInherits();
        // Traverse from higher to lower in the inheritance structure so more specific settings take precedence.
        for (ListIterator<String> inheritsIt = inherits.listIterator(inherits.size()); inheritsIt.hasPrevious(); ) {
            resolved.get(inheritsIt.previous())
                    .getOptions()
                    .forEach(inheritedOption -> options.put(inheritedOption.getKey(), inheritedOption));
        }
        // Now apply options from the type being processed.
        next.getOptions().forEach(opt -> options.put(opt.getKey(), opt));
        next.setOptions(new ArrayList<>(options.values()));
        next.setInherits(null);
    }
}
