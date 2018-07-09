import org.processmining.framework.boot.Boot
import org.processmining.framework.packages.PackageManager
import org.processmining.framework.plugin.PluginContext
import org.processmining.framework.plugin.PluginDescriptor
import org.processmining.framework.plugin.PluginManager
import org.processmining.framework.plugin.impl.PluginManagerImpl
import org.processmining.framework.util.PathHacker

import org.processmining.contexts.cli.CLI
import org.processmining.contexts.cli.CLIContext
import org.processmining.contexts.cli.CLIPluginContext
import org.processmining.contexts.scripting.Signature

def getJavaIdentifier(name) {
    def result = new StringBuilder()
    boolean underscoreAdded = false

    name = name.toLowerCase().trim()
    for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i)
        if ((('a' <= c) && (c <= 'z')) || (('A' <= c) && (c <= 'Z'))
                || ((result.length() > 0) && ('0' <= c) && (c <= '9'))) {
            result.append(c)
            underscoreAdded = false
        } else if (!underscoreAdded) {
            result.append("_")
            underscoreAdded = true
        }
    }

    return result.toString()
}

def getSignature(plugin, params) {
    String name

    if (plugin.hasAnnotation(CLI.class)) {
        name = plugin.getAnnotation(CLI.class).functionName()
    } else {
        name = plugin.name
    }

    return new Signature(plugin.returnTypes, getJavaIdentifier(name), params)
}

def addJarsFromPackageDirectory(dir, plugins) {
    dir.listFiles().each { f ->
        if (f.isDirectory()) {
            addJarsFromPackageDirectory(f, plugins)
        }
    }

    dir.listFiles().each { f ->
        if (f.getAbsolutePath().endsWith(PluginManager.JAR_EXTENSION)) {
            try {
                URL url = f.toURI().toURL()
                PROM_CLASS_LOADER.addURL(url)
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
    }
}

def addJarsForPackage(pack, plugins) {
    File dir = pack.getLocalPackageDirectory()
    if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) {
        println("  Error: package directory does not exist: ${dir}")
        return
    }

    // First, recusively iterate subfolders, where no scanning for plugins is necessary
    // this ensures all requires libraries are known when scanning for plugins
    dir.listFiles().each { f ->
        // Scan for jars. Only jars in the root of the package will be scanned for
        // plugins and other annotations.
        if (f.isDirectory()) {
            addJarsFromPackageDirectory(f, plugins)
            try {
                PROM_CLASS_LOADER.addURL(f.toURI().toURL())
            } catch (Exception e) {
            }
        }
    }
    // Now scan the jar files in the package root folder.
    dir.listFiles().each { f ->
        if (f.getAbsolutePath().endsWith(PluginManager.JAR_EXTENSION)) {
            try {
                URL url = f.toURI().toURL()
                println("  scanning for plugins: ${url}")
                PROM_CLASS_LOADER.addURL(url)
                if (f.getAbsolutePath().endsWith(PluginManager.JAR_EXTENSION)) {
                    plugins.register(url, pack, PROM_CLASS_LOADER)
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }

    }
    
    PathHacker.addLibraryPathFromDirectory(dir)
    try {
        PathHacker.addJar(dir.toURI().toURL())
        dir.listFiles().each { f ->
            if (f.isDirectory()) {
                PathHacker.addJar(f.toURI().toURL())
            }
        }
    } catch (Exception e) {
        assert (false)
    }
}

def buildParameters(parameterTypes, addType = false) {
    StringBuilder sb = new StringBuilder()

    int index = 0
    for (Class cl : parameterTypes) {
        if (index++ > 0) {
            sb.append(", ")
        }

        if (addType) {
            sb.append("${cl.canonicalName} ")
        }
        sb.append("p${index}")
    }

    return sb.toString()
}

if (binding.hasVariable('PROM_CLASS_LOADER')) {
    println("* Initializing...")

    def defaultUrls = PROM_CLASS_LOADER.getURLs()
    def packages = PackageManager.getInstance()

    println("* Loading packages...")
    packages.initialize(Boot.VERBOSE)

    println("* Loading plugins...")
    def pluginCache = [:]
    PluginManagerImpl.metaClass.static.cache = { key, value = null ->
        if (value == null) {
            return pluginCache[key]
        } else {
            return pluginCache[key] = value
        }
    }

    PluginManagerImpl.initialize(CLIPluginContext.class)
    def plugins = PluginManagerImpl.getInstance()

    packages.enabledPackages.each { p ->
        println(" - loading package: [${p.name} ${p.version}]")
        addJarsForPackage(p, plugins)
    }

    PROM_CLASS_LOADER.addURL(new File(PROM_LIB_DIR).toURI().toURL())

    defaultUrls.each { url ->
        if (!(new File(url.toURI()).getCanonicalPath().startsWith(PROM_LIB_DIR))) {
            println(" - registering library: ${url}")
            plugins.register(url, null, PROM_CLASS_LOADER)
        } else {
            // println("Skipping: ${url.file} while scanning for plugins.")
        }
    }

    pluginCache[null] = plugins.getAllPlugins()

    return
}

binding.setVariable('__plugin_context', new CLIContext().mainPluginContext)
binding.setVariable('__plugin_manager', PluginManagerImpl.getInstance())

binding.setVariable('prom', { String method, ... args ->
    if (!method) {
        throw new MissingMethodException("Please specify non-empty method name to proceed.")
    }

    int argsLength = args.length

    def results = null

    def plugins = __plugin_manager.cache(method)
    if (plugins == null) {
        plugins = __plugin_manager.cache(null)
    }

    plugins.each { plugin ->
        int methodIndex = 0
        plugin.parameterTypes.each { p ->
            Signature signature = getSignature(plugin, p)
            def paramTypes = signature.parameterTypes
            if (method == signature.name && argsLength == paramTypes.size()) {
                boolean matched = true;
                int argIndex = 0
                paramTypes.each {
                    if (!it.isAssignableFrom(args[argIndex++]?.getClass())) {
                        matched = false;
                        return
                    }
                }

                if (matched) {
                    println("* Calling method #${methodIndex} ${signature.name}(${buildParameters(signature.parameterTypes, true)})...")

                    def cachedPlugins = __plugin_manager.cache(method)
                    if (cachedPlugins) {
                        boolean duplicated = false
                        cachedPlugins.each { x ->
                            if (x.ID == plugin.ID) {
                                duplicated = true
                                return
                            }
                        }

                        if (!duplicated) {
                            cachedPlugins.add(plugin)
                        }
                    } else {
                        __plugin_manager.cache(method, [plugin])
                    }

                    results = plugin.invoke(methodIndex, __plugin_context.createChildContext("Result of ${signature.name}"), args)
                    return
                }
            }

            methodIndex++
        }
    }

    if (results == null) {
        def methods = []
        plugins.each { plugin ->
            plugin.parameterTypes.each { paramType ->
                Signature signature = getSignature(plugin, paramType)

                // FIXME based on similarity of method signature
                if (method == signature.name) {
                    methods.add(" - ${signature.name}(${buildParameters(signature.parameterTypes, true)})")
                }
            }
        }
        throw new MissingMethodException(
            "Method '${method}' not found! Possible solutions:\n${methods ? methods.join('\n') : ' - N/A'}")
    }

    results.synchronize()

    return results.expectedSize == 1 ? results.getResult(0) : results.results
})

/* slow and inefficient...
new File(PROM_CONTEXT_SCRIPT).withWriter(PROM_ENCODING) { w ->
    Set<Signature> availablePlugins = []

    w.writeLine('def __main_context = new org.processmining.contexts.cli.CLIContext().getMainPluginContext()')
    w.writeLine('def __plugin_manager = org.processmining.framework.plugin.impl.PluginManagerImpl.getInstance()')

    plugins.getAllPlugins().each { plugin ->
        w.writeLine("//")
        w.writeLine("// ${plugin.name} ID=[${plugin.ID}]")
        w.writeLine("//")

        for (int i = 0; i < plugin.getParameterTypes().size(); i++) {
            Signature signature = getSignature(plugin, i)

            if (!availablePlugins.contains(signature)) {
                availablePlugins.add(signature);

                w.writeLine("// ${signature}")
                w.writeLine("def ${signature.name}(${buildParameters(signature.parameterTypes, true)}) {")
                w.writeLine("    def context = __main_context.createChildContext(\"Result of ${signature.name}\")")
                w.writeLine("    String pluginId = \"\"\"${plugin.ID}\"\"\"")
                w.writeLine("    def results = __plugin_manager.getPlugin(pluginId).invoke(${i}, context, [${buildParameters(signature.parameterTypes)}] as Object[])");
                w.writeLine("    results?.synchronize()")
                w.writeLine("    return results?.expectedSize == 1 ? results.getResult(0) : results?.results")
                w.writeLine("}")
            }
        }
    }
}
*/