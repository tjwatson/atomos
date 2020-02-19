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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.felix.atomos.maven.ReflectConfig.ClassConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "atomos", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class AtomosMojo extends AbstractMojo
{

    private static final String ATOMOS_PATH = "ATOMOS";

    @Parameter(defaultValue = "${project.build.directory}/" + ATOMOS_PATH)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/" + "classpath_lib")
    private File classpath_lib;

    @Parameter(defaultValue = "graal.native.image.build.args")
    private String nativeImageArgsPropertyName;

    @Parameter(defaultValue = "${project}", required = true, readonly = false)
    private MavenProject project;

    @Parameter
    private String mainClass;

    @Parameter(defaultValue = "false") //TODO: CHECK GRAAL EE ONLY
    private boolean debug;

    @Parameter
    private String imageName;

    @Parameter
    private String nativeImageExecutable;

    @Parameter
    private List<String> additionalInitializeAtBuildTime;

    @Parameter
    private List<File> graalResourceConfigFile;

    public static boolean isJarFile(Path path)
    {
        try (JarFile j = new JarFile(path.toFile());)
        {

            return true;
        }
        catch (final IOException e)
        {

        }

        return false;
    }

    @Override
    public void execute() throws MojoExecutionException
    {
        getLog().info("outputDirectory" + outputDirectory);
        try
        {
            Files.createDirectories(outputDirectory.toPath());

            final Config config = new Config();
            config.outputDir = outputDirectory.toPath();
            config.mainClass = mainClass;
            config.additionalInitializeAtBuildTime = additionalInitializeAtBuildTime;
            if (imageName == null || imageName.isEmpty())
            {
                config.imageName = project.getArtifactId();
            }
            else
            {
                config.imageName = imageName;
            }

            if (graalResourceConfigFile != null && !graalResourceConfigFile.isEmpty())
            {
                config.resourceConfigs = graalResourceConfigFile.stream().map(
                    File::toPath).collect(Collectors.toList());
            }
            config.nativeImageExec = nativeImageExecutable;

            final List<Path> paths = Files.list(classpath_lib.toPath()).filter(
                AtomosMojo::isJarFile).collect(Collectors.toList());

            final Path p = SubstrateService.substrate(paths, config);

            final Map<String, ClassConfig> reflectConfigs = ReflectConfig.reflectConfig(
                paths, config);

            final ResourceConfigResult resourceConfigResult = ResourceConfig.resourceConfig(
                paths, config);

            final List<String> argsPath = BuildArgs.create(config, reflectConfigs,
                resourceConfigResult);

            paths.add(p);
            NativeImageBuilder.execute(config, paths, argsPath);
        }
        catch (

            final Exception e)
        {

            throw new MojoExecutionException("Error", e);
        }

    }

}
