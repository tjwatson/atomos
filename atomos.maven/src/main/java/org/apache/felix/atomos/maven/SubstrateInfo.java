package org.apache.felix.atomos.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class SubstrateInfo
{
    Path out;
    Path path;
    String id;
    String bsn;
    String version;
    List<String> files = new ArrayList<>();

}