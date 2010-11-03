/*
 * Copyright (c) 2010, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.xtreemfs.babudb.BabuDBInternal;

import static org.xtreemfs.babudb.BabuDBFactory.*;

/**
 * {@link ClassLoader} for accessing optional plugins for BabuDB.
 * Plugins may access BabuDB via BabuDB Main.start(BabuDBInternal babuDB).
 * 
 * @author flangner
 * @date 11/01/2010
 */
public final class PluginLoader extends ClassLoader {

    private final Map<String, byte[]>   classes = new HashMap<String, byte[]>();
    
    private BabuDBInternal              babuDB;
    
    /**
     * Creates a BabuDB JAR class loader for the plugins defined within the
     * configuration file. It will automatically attempt to load its JAR 
     * for the given data version.
     * 
     * @param babuDBImpl
     * 
     * @throws IOException if an I/O error occurred
     */
    private PluginLoader(BabuDBInternal babuDB) throws IOException {
        super(null);
        
        this.babuDB = babuDB;
        
        String main = null;
        int index = 0;
        for (String pluginPath : babuDB.getConfig().getPluginPaths()) {
            
            // load all classes from the plugin JARs
            JarInputStream jis = 
                new JarInputStream(getClass().getResourceAsStream(
                        pluginPath + "/" + BABUDB_VERSION + ".jar"));
            
            JarEntry next = null;
            while ((next = jis.getNextJarEntry()) != null) {
                
                if (!next.getName().endsWith(".class"))
                    continue;
                
                byte[] buf = new byte[4096];
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                
                int len = -1;
                while ((len = jis.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                
                String className = next.getName().substring(0, 
                        next.getName().length() - ".class".length())
                        .replace('/', '.');
                
                classes.put(className, out.toByteArray());
                out.close();
            }
            
            jis.close();
            
            if (main != null) {
                String configPath = 
                    babuDB.getConfig().getPluginConfigPaths().get(index);
                
                try {
                    Class<?> pluginMain = loadClass(main);
                    this.babuDB = (BabuDBInternal) pluginMain
                        .getMethod("start", BabuDBInternal.class, String.class)
                        .invoke(this.babuDB, configPath);
                    
                } catch (Exception e) {
                    throw new RuntimeException("Plugin at '" + pluginPath + "'" +
                    		" for version " + BABUDB_VERSION + 
                    		((configPath != null) ? " with " +
                    		"config at path " + configPath : "") +
                    		" could not be initialized!", e.getCause());
                }
                main = null;
            }
            index++;
        }
    }

    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        
        byte[] classBytes = classes.get(name);
        
        if (classBytes == null)
            return findSystemClass(name);
        
        Class<?> clazz = defineClass(name, classBytes, 0, classBytes.length);
        
        if (resolve)
            resolveClass(clazz);
        
        return clazz;
    }
    
    /**
     * Checks if a certain plugin is available to be loaded by this class 
     * loader.
     * 
     * @param name - of the plugin JAR.
     * 
     * @return <code>true</code>, if it is supported, <code>false</code>,
     *         otherwise.
     */
    public static boolean checkPluginSupport(String path) {
        
        JarInputStream jis = null;
        try {
            jis = new JarInputStream(PluginLoader.class.getResourceAsStream(
                    path + "/" + BABUDB_VERSION + ".jar"));
            return true;
        } catch (Exception exc) {
            return false;
        } finally {
            if (jis != null)
                try {
                    jis.close();
                } catch (IOException exc) {
                    // ignore
                }
        }   
    }
    
    /**
     * This methods loads all available plugins and allows the plugins to
     * overload the BabuDB API.
     * 
     * @param babuDB
     * @return the overloaded BabuDB.
     * 
     * @throws IOException if a plugin could not be loaded.
     */
    public final static BabuDBInternal init(BabuDBInternal babuDB) 
            throws IOException {
        
        PluginLoader loader = new PluginLoader(babuDB);
        return loader.babuDB;
    }
}
