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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.apache.felix.atomos.maven.scrmock.EmptyBundeLogger;
import org.apache.felix.atomos.maven.scrmock.PathBundle;
import org.apache.felix.scr.impl.logger.BundleLogger;
import org.apache.felix.scr.impl.metadata.ComponentMetadata;
import org.apache.felix.scr.impl.metadata.ReferenceMetadata;
import org.apache.felix.scr.impl.parser.KXml2SAXParser;
import org.apache.felix.scr.impl.xml.XmlHandler;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentConstants;

public class ReflectConfig
{
    private static final String CLASS_START = "{\n";
    private static final String CLASS_END = "}";
    private static final String COMMA = ",\n";
    private static final String CLASS_NAME = "\"name\":\"%s\"";
    private static final String FIELDS_START = "\"fields\" : [\n";
    private static final String FIELD_NAME = "{ \"name\" : \"%s\" }";
    private static final String FIELDS_END = "]";
    private static final String METHODS_START = "\"methods\" : [\n";
    private static final String METHOD_NAME = FIELD_NAME;
    private static final String METHODS_END = FIELDS_END;
    private static final String ACTIVATOR_CONSTRUCTOR = "\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[] }]";
    private static final String COMPONENT_CONSTRUCTOR = "\"allPublicConstructors\" : true";

    static class ClassConfig
    {
        final String className;
        String constructor;
        Set<String> fields = new TreeSet<>();
        Set<String> methods = new TreeSet<>();

        public ClassConfig(String className)
        {
            this.className = className;
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof ClassConfig))
            {
                return false;
            }
            return className == ((ClassConfig) other).className;
        }

        @Override
        public int hashCode()
        {
            return className.hashCode();
        }

        @Override
        public String toString()
        {
            final StringBuilder builder = new StringBuilder();
            builder.append(ind(1)).append(CLASS_START);

            builder.append(ind(2)).append(String.format(CLASS_NAME, className));

            Optional.ofNullable(constructor).ifPresent(
                (c) -> builder.append(COMMA).append(ind(2)).append(c));

            final AtomicReference<String> comma = new AtomicReference<>("");
            if (!fields.isEmpty())
            {
                builder.append(COMMA).append(ind(2)).append(FIELDS_START);
                fields.forEach(
                    f -> builder.append(comma.getAndSet(COMMA)).append(ind(3)).append(
                        String.format(FIELD_NAME, f)));
                builder.append('\n').append(ind(2)).append(FIELDS_END);
            }

            comma.set("");
            if (!methods.isEmpty())
            {
                builder.append(COMMA).append(ind(2)).append(METHODS_START);
                methods.forEach(
                    m -> builder.append(comma.getAndSet(COMMA)).append(ind(3)).append(
                        String.format(METHOD_NAME, m)));
                builder.append('\n').append(ind(2)).append(METHODS_END);
            }

            builder.append('\n').append(ind(1)).append(CLASS_END);

            return builder.toString();
        }
    }

    public static Map<String, ClassConfig> reflectConfig(List<Path> paths, Config config)
        throws Exception
    {

        final Map<String, ClassConfig> classes = new TreeMap<>();

        for (final Path p : paths)
        {
            try (JarFile jar = new JarFile(p.toFile()))
            {
                discoverActivators(jar, classes);
                discoverSeriviceComponents(paths, jar, classes);
            }
        }
        return classes;
    }

    /**
     * @throws IOException
     *
     */

    public static String createConfigContent(Map<String, ClassConfig> reflectConfigs)
    {
        final StringBuilder builder = new StringBuilder();
        builder.append('[').append('\n');
        final AtomicReference<String> comma = new AtomicReference<>("");

        final Collection<ClassConfig> configs = reflectConfigs.values();

        final List<ClassConfig> list = new ArrayList<>(configs);

        Collections.sort(list, new Comparator<ClassConfig>()
        {
            @Override
            public int compare(ClassConfig o1, ClassConfig o2)
            {
                return o1.className.compareTo(o2.className);
            }
        });

        for (final ClassConfig config : configs)
        {
            builder.append(comma.getAndSet(COMMA)).append(config.toString());
        }
        builder.append('\n').append(']');
        return builder.toString();
    }

    private static void discoverActivators(JarFile jar, Map<String, ClassConfig> classes)
        throws IOException
    {

        final Attributes attributes = jar.getManifest().getMainAttributes();
        String activator = attributes.getValue(Constants.BUNDLE_ACTIVATOR);
        if (activator == null)
        {
            activator = attributes.getValue(Constants.EXTENSION_BUNDLE_ACTIVATOR);
        }
        if (activator != null)
        {
            activator = activator.trim();
            final ClassConfig config = classes.computeIfAbsent(activator,
                (n) -> new ClassConfig(n));
            if (config.constructor == null)
            {
                config.constructor = ACTIVATOR_CONSTRUCTOR;
            }
        }
    }

    private static void discoverSeriviceComponents(List<Path> paths, JarFile jar,
        Map<String, ClassConfig> classes) throws Exception
    {
        final URL[] urls = paths.stream().map(p -> {
            try
            {
                return p.toUri().toURL();
            }
            catch (final MalformedURLException e1)
            {
                throw new UncheckedIOException(e1);
            }
        }).toArray(URL[]::new);

        final List<ComponentMetadata> cDDTOs = readCDDTO(jar);
        cDDTOs.forEach((c) -> {
            final Class<?> clazz;
            try (final URLClassLoader cl = new URLClassLoader(urls, null))
            {

                //TODO: try with res
                clazz = cl.loadClass(c.getImplementationClassName());
            }
            catch (final Exception e)
            {
                e.printStackTrace();
                return;
            }

            final ClassConfig config = classes.computeIfAbsent(clazz.getName(),
                (n) -> new ClassConfig(n));
            config.constructor = COMPONENT_CONSTRUCTOR;

            Optional.ofNullable(c.getActivate()).ifPresent(
                (m) -> addMethod(m, clazz, classes));
            Optional.ofNullable(c.getModified()).ifPresent(
                (m) -> addMethod(m, clazz, classes));
            Optional.ofNullable(c.getDeactivate()).ifPresent(
                (m) -> addMethod(m, clazz, classes));
            if (c.getActivationFields() != null)
            {
                for (final String fName : c.getActivationFields())
                {
                    addField(fName, clazz, classes);
                }
            }
            if (c.getDependencies() != null)
            {
                for (final ReferenceMetadata r : c.getDependencies())
                {
                    Optional.ofNullable(r.getField()).ifPresent(
                        (f) -> addField(f, clazz, classes));
                    Optional.ofNullable(r.getBind()).ifPresent(
                        (m) -> addMethod(m, clazz, classes));
                    Optional.ofNullable(r.getUpdated()).ifPresent(
                        (m) -> addMethod(m, clazz, classes));
                    Optional.ofNullable(r.getUnbind()).ifPresent(
                        (m) -> addMethod(m, clazz, classes));
                    Optional.ofNullable(r.getInterface()).ifPresent(
                        (i) -> classes.computeIfAbsent(i, (n) -> new ClassConfig(n)));
                }
            }
        });

    }

    private static List<ComponentMetadata> readCDDTO(JarFile jar) throws Exception
    {

        final BundleLogger logger = new EmptyBundeLogger();

        final List<ComponentMetadata> list = new ArrayList<>();

        final Attributes attributes = jar.getManifest().getMainAttributes();
        final String descriptorLocations = attributes.getValue(
            ComponentConstants.SERVICE_COMPONENT);

        if (descriptorLocations == null)
        {
            return list;
        }
        final StringTokenizer st = new StringTokenizer(descriptorLocations, ", ");

        while (st.hasMoreTokens())
        {
            final String descriptorLocation = st.nextToken();

            final InputStream stream = jar.getInputStream(
                jar.getEntry(descriptorLocation));

            final BufferedReader in = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"));

            final XmlHandler handler = new XmlHandler(new PathBundle(jar), logger, true,
                true);

            final KXml2SAXParser parser = new KXml2SAXParser(in);

            parser.parseXML(handler);

            list.addAll(handler.getComponentMetadataList());
        }
        return list;
    }

    private static void addMethod(String mName, Class<?> clazz,
        Map<String, ClassConfig> classes)
    {
        System.out.println("-----------" + clazz.getName() + ":" + mName);

        try
        {
            for (final Method m : clazz.getDeclaredMethods())
            {
                if (mName.equals(m.getName()))
                {
                    final ClassConfig config = classes.computeIfAbsent(clazz.getName(),
                        (n) -> new ClassConfig(n));
                    config.methods.add(mName);
                    return;
                }
            }
            final Class<?> superClass = clazz.getSuperclass();
            if (superClass != null)
            {
                addMethod(mName, superClass, classes);
            }
        }
        catch (final NoClassDefFoundError e)
        {
            // TODO: promlems with URLclassloaderHANDLE!!!!!!
            //            Caused by: java.lang.ClassNotFoundException: org.apache.felix.atomos.tests.testbundles.service.contract.Echo
            //            at java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:436)
            //            at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:588)
            //            at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:521)
            e.printStackTrace();
        }
    }

    private static void addField(String fName, Class<?> clazz,
        Map<String, ClassConfig> classes)
    {
        System.out.println("???" + clazz.getName() + ":" + fName);

        final boolean exists = Stream.of(clazz.getDeclaredFields()).anyMatch(
            f -> f.getName().equals(fName));
        if (exists)
        {
            final ClassConfig config = classes.computeIfAbsent(clazz.getName(),
                (n) -> new ClassConfig(n));
            config.fields.add(fName);
        }

        final Class<?> superClass = clazz.getSuperclass();
        if (superClass != null)
        {
            addField(fName, superClass, classes);
        }
    }

    private static Object ind(int num)
    {
        final StringBuilder indent = new StringBuilder();
        for (int i = 0; i < num; i++)
        {
            indent.append("  ");
        }
        return indent.toString();
    }

}
