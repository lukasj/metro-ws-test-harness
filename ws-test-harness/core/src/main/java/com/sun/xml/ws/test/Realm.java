/*
 * Copyright (c) 1997, 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.test;

import com.sun.istack.Nullable;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Represents a classloader.
 *
 * {@link Realm}s form a tree structure where children delegates
 * to the parent for classloading.
 *
 * @author Kohsuke Kawaguchi
 */
public class Realm {
    /**
     * Human readable name that identifies this realm for the debugging purpose.
     */
    private final String name;

    /**
     * Parent realm. Class loading delegates to this parent.
     */
    private final @Nullable Realm parent;

    /**
     * Jar files and class folders that are added.
     */
    private final Path classPath = new Path(World.project);
    private final Path modulePath = new Path(World.project);
    
    private ModuleLayer layer;
    private ModuleLayer.Controller contr;

    private AntClassLoader classLoader;
private ClassLoader x;
    public Realm(String name, Realm parent) {
        this.name = name;
        this.parent = parent;
    }

    public synchronized ClassLoader getClassLoader() {
        if(classLoader==null && x==null) {
            System.out.println("Boot mods:");
             for (Module m: ClassLoader.getPlatformClassLoader().getClass().getModule().getLayer().modules()) {
                    System.out.println("name: " + m.getName());
                    System.out.println("\tloader: " + m.getClassLoader());
                }       
             System.out.println("Done.");
            // delegates to the system classloader by default.
            // when invoked for debugging harness (with a lot of jars in system classloader),
            // this provides the easy debug path.
            // when invoked through bootstrap, this still provides the maximum isolation.
            ClassLoader pcl = parent!=null ? parent.getClassLoader() : createCL(ClassLoader.getPlatformClassLoader());
//            ClassLoader pcl = ClassLoader.getSystemClassLoader();

//            ModuleLayer currentModuleLayer = pcl != ClassLoader.getPlatformClassLoader()? parent.layer : ModuleLayer.boot();
            ModuleLayer currentModuleLayer = (pcl instanceof AntClassLoader) ? ModuleLayer.boot() : parent.layer;
//            ModuleLayer currentModuleLayer = ModuleLayer.boot();
            
            Set<java.nio.file.Path> modules = Arrays.stream(modulePath.toString().split(File.pathSeparator))
                    .map((el) -> Paths.get(el))
                    .collect(Collectors.toSet());
//            if (parent != null) {
//                    modules.addAll(
//                            Arrays.stream(parent.modulePath.toString().split(File.pathSeparator))
//                                .map((el) -> Paths.get(el))
//                                .collect(Collectors.toSet()));
//            }

            ModuleFinder moduleFinder = ModuleFinder.of(modules.toArray(new java.nio.file.Path[modules.size()]));
            ModuleFinder emptyFinder = ModuleFinder.of(new java.nio.file.Path[0]);
//            ModuleFinder emptyFinder = ModuleFinder.ofSystem();

            Set<String> moduleNames = moduleFinder.findAll().stream()
                    .map(moduleRef -> moduleRef.descriptor().name())
                    .filter(name -> !name.contains("istack"))
                    .collect(Collectors.toSet());
//            Set<String> moduleNames2 = emptyFinder.findAll().stream()
//                    .map(moduleRef -> moduleRef.descriptor().name())
//                    .filter(name -> !name.contains("istack"))
//                    .collect(Collectors.toSet());
////            moduleNames.addAll(moduleNames2);
//            moduleNames.add("java.base");
            System.out.println("Realm: " + name);
            System.out.println("modules: " + moduleNames);
            moduleFinder.findAll().iterator().forEachRemaining(m ->
                    {
                ModuleDescriptor descriptor = m.descriptor();
                        System.out.println(descriptor.name());
                        System.out.println(descriptor.toNameAndVersion());
                
            }
            );
            
            Configuration configuration = //parent != null
//                    currentModuleLayer.configuration().resolveAndBind(moduleFinder, emptyFinder, moduleNames);
                    currentModuleLayer.configuration().resolveAndBind(emptyFinder, moduleFinder, moduleNames);
//                    ? currentModuleLayer.configuration().resolveAndBind(moduleFinder, emptyFinder, moduleNames)
//                    : currentModuleLayer.configuration().resolve(moduleFinder, emptyFinder, moduleNames);
//        Configuration configuration = currentModuleLayer.configuration().resolve(moduleFinder, emptyFinder, moduleNames);
            final ModuleLayer.Controller controller = currentModuleLayer.defineModulesWithOneLoader(configuration, List.of(currentModuleLayer), pcl);
            contr = controller;
            if ("runtime".equals(name)) {
                Module unnamed = pcl.getParent().getUnnamedModule();
//                for (Module m: controller.layer().modules()) {
//                    System.out.println("name: " + m.getName());
//                    System.out.println("loader: " + m.getClassLoader().getUnnamedModule());
//                }
//                System.out.println("== FOUND: " + controller.layer().findModule("com.sun.xml.ws").get());
//                System.out.println("== FOUND: " + controller.layer().findLoader("com.sun.xml.ws").getUnnamedModule());
//                System.out.println("== FOUND: " + pcl.getUnnamedModule());
////                System.out.println("== FOUND: " + controller.layer().findModule("com.sun.xml.ws").get());
//                System.out.println("== FOUND: " + pcl.getParent().getUnnamedModule());
//                System.out.println("== FOUND: " + pcl.getParent().getParent().getUnnamedModule());
//                System.out.println("== FOUND: " + pcl.getUnnamedModule());
                Module jaxwsImpl = controller.layer().findModule("com.sun.xml.ws").get();
                controller.addReads(jaxwsImpl, unnamed);
                controller.addOpens(jaxwsImpl, "com.sun.xml.ws.fault", unnamed);
//                controller.addOpens(controller.layer().findModule("java.base").get(), "jdk.internal.misc", jaxwsImpl);
//                controller.addReads(controller.layer().findLoader("com.sun.xml.ws").getUnnamedModule(), controller.layer().findModule("com.sun.xml.ws").get());

                Module jaxbImpl = controller.layer().findModule("com.sun.xml.bind").get();
                System.out.println("JAXB IMPL: " + jaxbImpl);
//                controller.addExports(controller.layer().findModule("com.sun.tools.xjc").get(),
//                        "com.sun.tools.xjc.generator.bean",
//                        jaxbImpl);
//                controller.addOpens(jaxbImpl,
//                    "com.sun.xml.bind.v2.model.nav",
//                    controller.layer().findModule("com.sun.tools.xjc").get());

                Module saajImpl = controller.layer().findModule("com.sun.xml.messaging.saaj").get();
                System.out.println("SAAJ IMPL: " + saajImpl);
                controller.addReads(saajImpl, unnamed);
                controller.addExports(saajImpl,
                        "com.sun.xml.messaging.saaj.soap.impl",
                        unnamed);
                controller.addExports(saajImpl,
                        "com.sun.xml.messaging.saaj.soap.ver1_1",
                        unnamed);
                controller.addOpens(saajImpl,
                    "com.sun.xml.messaging.saaj.soap.impl",
                    unnamed);

                controller.addOpens(controller.layer().findModule("java.xml.ws").get(),
                    "javax.xml.ws.wsaddressing",
                    controller.layer().findModule("java.xml.bind").get());
                controller.addOpens(controller.layer().findModule("java.xml.ws").get(),
                    "javax.xml.ws.wsaddressing",
                    unnamed);
            }
//        controller.addReads(controller.layer().findModule("java.xml.soap").get(), controller.layer().findModule("com.sun.xml.messaging.saaj").get());
//        controller.addReads(controller.layer().findModule("com.sun.xml.messaging.saaj").get(), controller.layer().findModule("java.xml.soap").get());
//            if (false && parent != null && controller.layer().findModule("com.sun.tools.xjc").isPresent()) {
            if ("tool".equals(name) && controller.layer().findModule("com.sun.tools.xjc").isPresent()) {
//                Module impl = controller.layer().findModule("com.sun.xml.bind").get();
//                System.out.println("IMPL: " + impl);
//                controller.addExports(controller.layer().findModule("com.sun.tools.xjc").get(),
//                        "com.sun.tools.xjc.generator.bean",
//                        impl);
//                controller.addOpens(impl,
//                    "com.sun.xml.bind.v2.model.nav",
//                    controller.layer().findModule("com.sun.tools.xjc").get());
//                controller.addOpens(controller.layer().findModule("com.sun.xml.bind").get(),
//                    "com.sun.xml.bind.v2.model.nav",
//                    controller.layer().findModule("com.sun.tools.ws.jaxws").get());
            }
            layer = controller.layer();
            
//            URLClassLoader ucl = null;
//            if (classPath != null) {
            Set<URL> urls = new HashSet<>();
            for (String s : classPath.toString().split(File.pathSeparator)) {
                try {
//                URL u = new URL("file://" + s);
                URL u = new File(s).toURI().toURL();
                urls.add(u);
            System.out.println("added cp: " + u.toString());
                } catch (MalformedURLException mue) {
                    throw new RuntimeException(mue);
                }
            }
//                URLClassLoader ucl = new URLClassLoader(urls.toArray(new URL[urls.size()]), layer.findLoader(moduleNames.iterator().next()));
//            }
            ClassLoader r = layer.findLoader(moduleNames.iterator().next());
//            classLoader = createCL(r);
            x = r;
        }

        return x;
    }

    /**
     * Adds a single jar.
     */
    public void addJar(File jar) throws IOException {
        assert classLoader==null : "classLoader is already created";
        if(!jar.exists())
            throw new IOException("No such file: "+jar);
        if (isModule(jar)) {
            modulePath.createPathElement().setLocation(jar);
        } else {
            if (!(jar.getName().contains("pfl") || jar.getName().contains("management-api-3.2.2")
                    || jar.getName().contains("ha-api-3.1.12"))) {
                System.out.println("created: " + jar.getName());
            classPath.createPathElement().setLocation(jar);
            }
        }
    }

    /**
     * Adds a single class folder.
     */
    public void addClassFolder(File classFolder) throws IOException {
        addJar(classFolder);
    }

    /**
     * Adds all jars in the given folder.
     *
     * @param folder
     *      A directory that contains a bunch of jar files.
     * @param excludes
     *      List of jars to be excluded
     */
    public void addJarFolder(File folder, final String... excludes) throws IOException {
        if(!folder.isDirectory())
            throw new IOException("Not a directory "+folder);

        File[] children = folder.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                for (String name : excludes) {
                    if(pathname.getName().equals(name))
                        return false;   // excluded
                }
                return pathname.getPath().endsWith(".jar");
            }
        });

        for (File child : children) {
            addJar(child);
        }
    }

    public void dump(PrintStream out) {
        out.println("classPath:");
        for( String item : classPath.toString().split(File.pathSeparator)) {
            out.println("  "+item);
        }
        out.println("modulePath:");
        for( String item : modulePath.toString().split(File.pathSeparator)) {
            out.println("  "+item);
        }
    }

    public String toString() {
        return name+" realm";
    }

    /**
     * List all the components in this realm (excluding those defined in parent.)
     */
    public File[] list() {
        String[] names = classPath.list();
        File[] r = new File[names.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = new File(names[i]);
        }
        return r;
    }

    public Path getPath() {
        return classPath;
    }

    public Path getModulePath() {
        return modulePath;
    }

    public Class loadClass(String className) throws ClassNotFoundException {
        ClassLoader cl = getClassLoader();
        try {
            cl.getParent().loadClass(className);
        } catch (ClassNotFoundException cnfe) {
            
        }
        return cl.loadClass(className);
    }

    public void addHarnessDeps() {
        for (String s : System.getProperty("java.class.path").split(File.pathSeparator)) {
            try {
            File f  = new File(s);
            if (!isModule(f)) {
                addJar(f);
            }
            } catch (IOException ioe) {}
        }
    }

    private boolean isModule(File f) {
        try {
        if (f.getName().endsWith(".jar")) {
            try {
                JarFile jf = new JarFile(f);
                if (jf.getEntry("module-info.class") != null) {
                    return true;
                }
                if (jf.getManifest().getMainAttributes().getValue("Automatic-Module-Name") != null) {
                    return true;
                }
            } catch (IOException ioe) {
            }
            return false;
        }
        if (f.isDirectory()) {
            if (new File(f, "module-info.class").exists()) {
                return true;
            }
            File m = new File(f, "META-INF/MANIFEST.MF");
            if (m.exists()) {
                try (InputStream is = Files.newInputStream(m.toPath(), StandardOpenOption.READ)) {
                    Manifest mf = new Manifest(is);
                    if (mf.getMainAttributes().getValue("Automatic-Module-Name") != null) {
                        return true;
                    }
                }
            }

        }
        } catch (IOException ioe) {}
        return false;
    }
    
    private AntClassLoader createCL(ClassLoader parent) {
        AntClassLoader cl = new AntClassLoader(parent, true) {
//            classLoader = new AntClassLoader(ucl, false) {
            @Override
            protected URL findResource(/*String moduleName, */String name) {
//                    if (moduleName != null) {
//                        try {
                URL u = getParent().getResource(name);
                if (u != null) {
                    return u;
                }
//                        } catch (ClassNotFoundException ex) {
//                            Logger.getLogger(Realm.class.getName()).log(Level.SEVERE, null, ex);
//                        }
//                        return getParent().getParent().getResource(name);
//                    }
                return super.findResource(name);
            }

            @Override
            public Class<?> findClass(/*String moduleName,*/String name) {
//                    if (moduleName != null) {
                try {
                    return getParent().loadClass(name);
                } catch (ClassNotFoundException ex) {
//                            Logger.getLogger(Realm.class.getName()).log(Level.SEVERE, null, ex);
                }
//                    }

                try {
                    return super.findClass(name);
                } catch (ClassNotFoundException cnfe) {
                }
                return null;
            }
        };
//            classLoader.setParent(pcl);
        cl.setProject(World.project);
//            if (modulePath != null) {
//                Path copy = ((Path) classPath.clone());
//                copy.add(modulePath);
//                classLoader.setClassPath(copy);
//            } else {
        cl.setClassPath(classPath);
//            }
        cl.setDefaultAssertionStatus(true);
        return cl;
    }
}
