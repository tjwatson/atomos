/**
 *
 */
package org.apache.felix.atomos.maven;

import java.nio.file.Path;
import java.util.List;

public class Config
{

    public Path outputDir;
    public String mainClass;
    public String imageName;
    public String nativeImageExec;
    public List<String> additionalInitializeAtBuildTime;
    public boolean debug = false;
    public List<Path> resourceConfigs;

}
