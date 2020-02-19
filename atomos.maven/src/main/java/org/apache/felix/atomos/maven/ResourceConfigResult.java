package org.apache.felix.atomos.maven;

import java.util.Set;
import java.util.TreeSet;

class ResourceConfigResult
{
    Set<String> allResourceBundles = new TreeSet<>();
    Set<String> allResourcePatterns = new TreeSet<>();
    Set<String> allResourcePackages = new TreeSet<>();
}