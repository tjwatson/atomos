/**
 *
 */
package org.apache.felix.atomos.maven;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NativeImageBuilder
{

    public static void execute(Config config, List<Path> classpath, List<String> args)
    {
        try
        {
            final Path exec = findNativeImageExecutable(config);

            final String cp = classpath.stream().map(
                p -> p.toAbsolutePath().toString()).collect(Collectors.joining(":"));

            final List<String> commands = new ArrayList<>();
            commands.add(exec.toAbsolutePath().toString());
            commands.add("-cp");
            commands.add(cp);
            commands.addAll(args);

            final ProcessBuilder pB = new ProcessBuilder(commands);
            pB.inheritIO();
            pB.directory(config.outputDir.toFile());

            final String cmds = pB.command().stream().collect(Collectors.joining(" "));

            System.out.println(cmds);

            final Process process = pB.start();
            final int exitValue = process.waitFor();
            if (exitValue != 0)
            {
                System.out.println("Wrong exit Value: " + exitValue);
            }
            else
            {
                System.out.println("works!!");
            }
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * @param config
     * @return
     */
    private static Path findNativeImageExecutable(Config config)
    {

        Path exec = null;
        if (config.nativeImageExec != null)
        {
            exec = findNativeImageExec(Paths.get(config.nativeImageExec));
        }
        if (exec == null)
        {
            exec = findNativeImageExec(Paths.get("native-image"));
        }
        if (exec == null && System.getenv("GRAAL_HOME") != null)
        {
            exec = findNativeImageExec(Paths.get(System.getenv("GRAAL_HOME")));
        }

        if (exec == null && System.getProperty("java.home") != null)
        {
            exec = findNativeImageExec(Paths.get(System.getProperty("java.home")));
        }
        return exec;
    }

    /**
     * @param path
     */
    private static Path findNativeImageExec(Path path)
    {

        Path candidate = null;
        if (!Files.exists(path))
        {
            return candidate;
        }
        if (Files.isDirectory(path))
        {
            candidate = findNativeImageExec(path.resolve("native-image"));

            if (candidate == null)
            {
                candidate = findNativeImageExec(path.resolve("bin"));
            }

        }
        else //file o
        {

            try
            {
                final ProcessBuilder processBuilder = new ProcessBuilder(path.toString(),
                    "--version");

                final Process versionProcess = processBuilder.start();
                final Stream<String> lines = new BufferedReader(
                    new InputStreamReader(versionProcess.getInputStream())).lines();
                final Optional<String> versionLine = lines.filter(
                    l -> l.contains("GraalVM Version")).findFirst();

                if (!versionLine.isEmpty())
                {
                    System.out.println(versionLine.get());
                    candidate = path;
                }
            }
            catch (final IOException e)
            {
                e.printStackTrace();
            }

        }
        return candidate;

    }
}
