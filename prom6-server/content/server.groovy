import groovy.lang.Binding
import groovy.lang.GroovyClassLoader
import groovy.lang.GroovyShell
import groovy.transform.Field

import java.util.concurrent.Executors
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

@Field final int DEFAULT_PORT = 5678

@Field final String HTTP_GET_METHOD = 'GET'
@Field final String HTTP_POST_METHOD = 'POST'

@Field final String HEADER_CONTENT_TYPE = 'Content-Type'

@Field final String DEFAULT_ENCODING = 'UTF-8'
@Field final String DEFAULT_CONTENT_TYPE = 'application/json'

@Field final String ERROR_NO_SCRIPT = "Script file not specified!"
@Field final String ERROR_NOT_SUPPORTED = "Sorry, only ${HTTP_GET_METHOD} and ${HTTP_POST_METHOD} methods are supported for this endpoint."
@Field final String ERROR_SOMETHING_WRONG = "Something wrong with server processing:\n"

@Field final String PROM_MAJOR_VERSION = System.env.get('PROM_MAJOR_VERSION')
@Field final String PROM_MINOR_VERSION = System.env.get('PROM_MINOR_VERSION')

@Field final String SPARK_JAVA_VERSION = System.env.get('SPARK_JAVA_VERSION')

@Field final String PROM_HOME = System.env.get('PROM_HOME')
@Field final String PROM_DIST_DIR = "${PROM_HOME}/ProM${PROM_MAJOR_VERSION}${PROM_MINOR_VERSION}_dist"
@Field final String PROM_LIB_DIR = "${PROM_HOME}/ProM${PROM_MAJOR_VERSION}${PROM_MINOR_VERSION}_lib"

@Field final String INIT_SCRIPT = 'init.groovy'
@Field final String TMP_DIR = '/tmp'

@Field final String[] EMPTY_STRING_ARRAY = new String[0]
@Field final String[] SCRIPT_SEARCH_PATHS = ["${PROM_HOME}/scripts", TMP_DIR]

@Field final GroovyClassLoader promClassLoader = new GroovyClassLoader()

[PROM_DIST_DIR, PROM_LIB_DIR].each { dir ->
    new File(dir).listFiles().each {
        if (it.name.endsWith('.jar')) {
            promClassLoader.addURL(it.toURI().toURL())
        }
    }
}

System.setProperty('java.library.path', PROM_LIB_DIR)

def sendResponse(exchange, code, message = '') {
    if (code == 200) {
        exchange.responseHeaders[HEADER_CONTENT_TYPE] = DEFAULT_CONTENT_TYPE;
    }

    exchange.sendResponseHeaders(code, message ? message.length() : 0)
    exchange.getResponseBody().withWriter(DEFAULT_ENCODING) {
        it.write(message)
    }
}

def extractParameters(query) {
    def params = [:]
    
    query?.split('&').each {
        def idx = it.indexOf('=');
        if (idx >= 0) {
            params.put(URLDecoder.decode(it.substring(0, idx), DEFAULT_ENCODING),
                URLDecoder.decode(it.substring(idx + 1), DEFAULT_ENCODING));
        }
    }
    
    return params
}

def normalizeFileName(fileName) {
    def normalizedName;

    if (fileName) {
        normalizedName = fileName.tokenize('/')[-1].toLowerCase()
        normalizedName = URLEncoder.encode(normalizedName)
        if (normalizedName.indexOf('.') < 0) {
            normalizedName = "${normalizedName}.groovy"
        }
    } else {
        normalizedName = "generated-script-t${Thread.currentThread().id}.groovy"
    }

    return normalizedName;
}

def initProM() {
    // def classLoader = new GroovyClassLoader(promClassLoader)
    def binding = new Binding([
        PROM_HOME: PROM_HOME,
        PROM_DIST_DIR: PROM_DIST_DIR,
        PROM_LIB_DIR: PROM_LIB_DIR,
        PROM_CLASS_LOADER: promClassLoader,
    ]);

    def shell = new GroovyShell(promClassLoader, binding)

    shell.run(new File(INIT_SCRIPT), EMPTY_STRING_ARRAY)
}

def runScript(scriptFileName, buildContext=true) {
    File file = null;

    if (scriptFileName?.startsWith('/')) {
        file = new File(scriptFileName)
    } else {
        scriptFileName = normalizeFileName(scriptFileName)
        SCRIPT_SEARCH_PATHS.each {
            File f = new File("${it}/${scriptFileName}")
            if (f.exists()) {
                file = f
                return
            }
        }
    }

    if (file == null || !file.exists()) {
        throw new IOException("${scriptFileName} not found!")
    }

    def requestId = UUID.randomUUID().toString()

    def binding = new Binding([
        PROM_HOME: PROM_HOME,
    ]);
    def shell = new GroovyShell(new GroovyClassLoader(promClassLoader), binding)
    if (buildContext) {
        shell.run(new File(INIT_SCRIPT), EMPTY_STRING_ARRAY)
    }

    return new groovy.json.JsonBuilder([
        id: requestId,
        script: scriptFileName,
        result: shell.run(file, EMPTY_STRING_ARRAY)
    ]).toString()
}

HttpServer server = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);
server.createContext("/script", [ handle: { exchange ->
    try {
        // FIXME supported parameters in URL:
        // - file: script file name(without preceding path)
        // - context: true if you need context being built before calling your script, defaults to true
        // - async: true or false; defaults to false
        def params = extractParameters(exchange.requestURI.query)

        switch(exchange.requestMethod) {
            case HTTP_GET_METHOD:
                if (!params.file) {
                    throw new Exception(ERROR_NO_SCRIPT)
                }
                sendResponse(exchange, 200, runScript(params.file))
                break

            case HTTP_POST_METHOD:
                String scriptFileName = "${TMP_DIR}/${normalizeFileName(params.file)}"

                new File(scriptFileName).withWriter(DEFAULT_ENCODING) { w ->
                    w.writeLine(exchange.requestBody.text)
                }

                sendResponse(exchange, 200, runScript(scriptFileName))
                break
            default:
                sendResponse(exchange, 401, ERROR_NOT_SUPPORTED)
                break
        }
    } catch(Exception e) {
        e.printStackTrace()
        sendResponse(exchange, 500, "${ERROR_SOMETHING_WRONG}${e.message}")
    }
} ] as HttpHandler)


initProM()

println "Starting service http://localhost:${DEFAULT_PORT}/script..."

// FIXME make thread pool size configurable...
server.setExecutor(Executors.newFixedThreadPool(5))
server.start()