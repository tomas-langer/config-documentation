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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

/**
 * Main class to start generating data.
 */
public class Main {
    /**
     * Main method.
     *
     * @param args expects the GAV of documented module and target directory
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) {
            throw new IllegalArgumentException("This tool requires two parameters: module name, the target path and optionally "
                                                       + "relative path to the generated docs from the file they will be included in");
        }
        String module = args[0];
        String targetPath = args[1];
        String relativePath;
        if (args.length == 2) {
            relativePath = "{rootdir}/config/";
        } else {
            relativePath = args[2];
            if (!relativePath.endsWith(File.separator) && !relativePath.endsWith("/")) {
                relativePath += "/";
            }
        }

        Path path = Paths.get(targetPath).toAbsolutePath().normalize();
        if (Files.exists(path) && Files.isDirectory(path)) {
            ConfigDocumentation docs = new ConfigDocumentation(path, relativePath, modulePredicate(module));
            docs.process();
        } else {
            throw new IllegalArgumentException("Target path must be a directory and must exist: "
                                                       + path.toAbsolutePath().normalize());
        }
    }

    private static Predicate<String> modulePredicate(String module) {
        if ("*".equals(module)) {
            System.out.println("Will document all modules on classpath");
            return it -> true;
        }
        return module::equals;
    }
}
