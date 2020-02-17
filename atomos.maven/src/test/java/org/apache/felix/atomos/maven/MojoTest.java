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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.felix.atomos.maven.ReflectConfig.ClassConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MojoTest
{

    /**
     *
     */
    private static final String ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl-";
    /**
     *
     */
    private static final String ATOMOS_TESTS_TESTBUNDLES_RESOURCE_A = "org.apache.felix.atomos.tests.testbundles.resource.a-";

    @Test
    void testFull(@TempDir Path tempDir) throws Exception
    {

        final List<Path> paths = getTestDependencyFiles();

        final Config config = new Config();
        config.outputDir = tempDir;

        SubstrateService.substrate(paths, config);
        final Map<String, ClassConfig> reflectConfigs = ReflectConfig.reflectConfig(paths,
            config);
        final ResourceConfigResult resourceConfigResult = ResourceConfig.resourceConfig(
            paths, config);

        final List<String> args = BuildArgs.create(config, reflectConfigs,
            resourceConfigResult);

        for (final String arg : args)
        {
            System.out.println(arg);

        }
        System.out.println(tempDir);
        System.out.println(tempDir);
    }

    @Test
    void testSubstrate(@TempDir Path tempDir) throws Exception
    {

        final Path path = getTestDependencyFileTested(
            ATOMOS_TESTS_TESTBUNDLES_RESOURCE_A);

        final Config config = new Config();
        config.outputDir = tempDir;
        SubstrateService.substrate(Arrays.asList(path), config);
    }

    @Test
    void testReflect(@TempDir Path tempDir) throws Exception
    {

        final List<Path> paths = getTestDependencyFiles();
        final Config config = new Config();
        config.outputDir = tempDir;
        final Map<String, ClassConfig> map = ReflectConfig.reflectConfig(paths, config);

        assertThat(map).isNotNull().containsOnlyKeys(
            "org.apache.felix.atomos.tests.testbundles.service.contract.Echo",
            "org.apache.felix.atomos.tests.testbundles.service.impl.EchoImpl",
            "org.apache.felix.atomos.tests.testbundles.service.impl.a.EchoImpl",
            "org.apache.felix.atomos.tests.testbundles.service.impl.activator.Activator",
            "org.apache.felix.atomos.tests.testbundles.service.impl.b.EchoImpl",
            "org.apache.felix.atomos.tests.testbundles.service.user.EchoUser");
        System.out.println(map);

    }

    @Test
    void testReflectMany(@TempDir Path tempDir) throws Exception
    {

        final List<Path> paths = getTestFiles("target/test-dependencies/");
        final Config config = new Config();
        config.outputDir = tempDir;
        final Map<String, ClassConfig> map = ReflectConfig.reflectConfig(paths, config);
        assertThat(map).isNotNull();

        System.out.println(map);

    }

    private List<Path> getTestDependencyFiles() throws IOException
    {

        return getTestFiles("target/test-dependencies/");
    }

    private List<Path> getTestFiles(String dir) throws IOException
    {
        final Path testDepsDir = Paths.get(dir);

        System.out.println(testDepsDir);
        return Files.list(testDepsDir).peek(System.out::println).filter(
            p -> p.toString().endsWith(".jar")).collect(
                Collectors.toList());
    }

    private Path getTestDependencyFileTested(String depName) throws IOException
    {
        final Path testDepsDir = Paths.get("target/test-dependencies/");
        final List<Path> paths = Files.list(testDepsDir).filter(
            p -> p.getFileName().toString().startsWith(depName)).collect(
                Collectors.toList());

        assertEquals(1, paths.size(),
            String.format("Must be exact one test Dependency with the name ?*", depName));
        return paths.get(0);
    }
}