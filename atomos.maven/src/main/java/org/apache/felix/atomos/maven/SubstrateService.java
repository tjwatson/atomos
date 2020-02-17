/*
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
package org.apache.felix.atomos.maven;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.framework.Constants;

public class SubstrateService
{

    private static final String SUBSTRATE_LIB = "substrate_lib";
    private static final Collection<String> DEFAULT_EXCLUDE_NAMES = Arrays.asList(
        "about.html", "DEPENDENCIES", "LICENSE", "NOTICE", "changelog.txt",
        "LICENSE.txt");
    private static final Collection<String> DEFAULT_EXCLUDE_PATHS = Arrays.asList(
        "META-INF/maven/", "OSGI-OPT/");
    private static final String ATOMOS_BUNDLES = "atomos";
    private static final String ATOMOS_BUNDLES_INDEX = ATOMOS_BUNDLES + "bundles.index";
    private static final String ATOMOS_BUNDLE_SEPARATOR = "-----ATOMOS-BUNDLE-SEPARATOR-----";

    enum EntryType
    {
        PACKAGE, NON_PACKAGE, PACKAGE_CLASS, PACKAGE_RESOURCE, DEFAULT_PACKAGE_CLASS, DEFAULT_PACKAGE_RESOURCE, NON_PACKAGE_RESOURCE
    }

    private static boolean isClass(String path)
    {
        return path.endsWith(".class");
    }

    private static boolean filter(JarEntry entry, Config config)
    {
        final String path = entry.getName();
        if (entry.isDirectory() || isClass(path))
        {
            return false;
        }
        for (final String excludedPath : DEFAULT_EXCLUDE_PATHS)
        {
            if (path.startsWith(excludedPath))
            {
                return false;
            }
        }
        for (final String excludedName : DEFAULT_EXCLUDE_NAMES)
        {
            if (path.endsWith(excludedName))
            {
                return false;
            }
        }
        return true;
    }

    public static void substrate(List<Path> files, Config config)
        throws IOException, NoSuchAlgorithmException
    {
        if (!config.outputDir.toFile().isDirectory())
        {
            throw new IllegalArgumentException(
                "Output file must be a directory." + config.outputDir);
        }
        if (!config.outputDir.toFile().exists())
        {
            Files.createDirectories(config.outputDir.resolve(SUBSTRATE_LIB));
        }

        final List<String> resources = new ArrayList<>();

        final AtomicLong counter = new AtomicLong(0);

        final Stream<SubstrateInfo> bis = files.stream()//
            .map(path -> create(counter.getAndIncrement(), path, config))//
            .peek(System.out::println);

        bis.forEach(s -> {
            resources.add(ATOMOS_BUNDLE_SEPARATOR);
            resources.add(s.id);
            resources.add(s.bsn);
            resources.add(s.version);
            s.files.forEach(resources::add);
        });
        writeBundleIndexFile(config.outputDir, resources);
        System.out.println("end");
    }

    private static void writeBundleIndexFile(Path output, final List<String> resources)
        throws IOException
    {
        final Path bundlesIndex = output.resolve(ATOMOS_BUNDLES_INDEX);

        Files.newBufferedWriter(bundlesIndex);

        try (BufferedWriter writer = Files.newBufferedWriter(bundlesIndex);)
        {
            resources.forEach((l) -> {
                try
                {
                    writer.append(l).append('\n');
                }
                catch (final IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    static SubstrateInfo create(long id, Path path, Config config)
    {
        final SubstrateInfo info = new SubstrateInfo();
        info.path = path;
        try (final JarFile jar = new JarFile(info.path.toFile()))
        {
            final Attributes attributes = jar.getManifest().getMainAttributes();
            info.bsn = attributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
            info.version = attributes.getValue(Constants.BUNDLE_VERSION);
            info.id = Long.toString(id);

            final Path out = config.outputDir.resolve(SUBSTRATE_LIB).resolve(info.id);

            Files.createDirectories(out);
            info.out = out;

            info.files = jar.stream().filter(j -> filter(j, config)).peek(j -> {
                try
                {
                    final Path target = out.resolve(j.getName());
                    Files.createDirectories(target);
                    Files.copy(jar.getInputStream(j), target,
                        StandardCopyOption.REPLACE_EXISTING);
                }
                catch (final IOException e)
                {
                    throw new UncheckedIOException(e);
                }

            }).peek(System.out::println).map(JarEntry::getName).collect(
                Collectors.toList());
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return info;
    }
}
