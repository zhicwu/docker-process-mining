import groovy.lang.Binding
import groovy.lang.GroovyClassLoader
import groovy.lang.GroovyCodeSource
import groovy.lang.GroovyShell
import groovy.transform.Field
import groovy.transform.Memoized

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Function

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalListener

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

@Field final int DEFAULT_PORT = 5678

@Field final String HTTP_GET_METHOD = 'GET'
@Field final String HTTP_POST_METHOD = 'POST'

@Field final String HTTP_WELCOME_PAGE = 'index.html'

@Field final String HEADER_CONTENT_TYPE = 'Content-Type'
@Field final String HEADER_REQUEST_ID = 'ProM-Request-ID'
@Field final String HEADER_CORS_ORIGIN = 'Access-Control-Allow-Origin'

@Field final String DEFAULT_CONTENT_TYPE = 'application/json'
@Field final String DEFAULT_CORS_ORIGIN = '*'
@Field final String DEFAULT_ENCODING = 'UTF-8'
@Field final String DEFAULT_MIME_TYPE = 'text/plain'
@Field final String DEFAULT_SCRIPT_EXT = '.groovy'

@Field final String ERROR_NO_OUTPUT_FILE = "Output file not found!"
@Field final String ERROR_NO_REQUEST_ID = "Request id not specified!"
@Field final String ERROR_NO_RESULT = "Result not available!"
@Field final String ERROR_NO_SCRIPT = "Script file not specified!"
@Field final String ERROR_NOT_SUPPORTED = "The HTTP method used in your request is supported for this endpoint."
@Field final String ERROR_SOMETHING_WRONG = "Something wrong when processing the request. Please check server log!"
@Field final String ERROR_UNFINISHED_REQUEST = "Please wait until the request being processed!"

@Field final int PROM_BACKLOG_SIZE = System.env.get('PROM_BACKLOG_SIZE') as Integer ?: 10
@Field final int PROM_HTTPD_THREADS = System.env.get('PROM_HTTPD_THREADS') as Integer ?: 5
@Field final int PROM_RESULT_CACHE_SIZE = System.env.get('PROM_RESULT_CACHE_SIZE') as Integer ?: 100
@Field final int PROM_RESULT_EXPIRE_MINUTE = System.env.get('PROM_RESULT_EXPIRE_MINUTE') as Integer ?: 60
@Field final int PROM_WORKER_THREADS = System.env.get('PROM_WORKER_THREADS') as Integer ?: 3

@Field final String PROM_WEB_CONTEXT = "${('/' + ((System.env.get('PROM_WEB_CONTEXT') ?: '').tokenize('/')[0] ?: '')).replaceAll('/$', '')}"

@Field final int PROM_MAJOR_VERSION = System.env.get('PROM_MAJOR_VERSION') as Integer ?: 6
@Field final int PROM_MINOR_VERSION = System.env.get('PROM_MINOR_VERSION') as Integer ?: 8

@Field final String PROM_HOME = System.env.get('PROM_HOME') ?: '/prom'
@Field final String PROM_DIST_DIR = "${PROM_HOME}/ProM${PROM_MAJOR_VERSION}${PROM_MINOR_VERSION}_dist"
@Field final String PROM_LIB_DIR = "${PROM_HOME}/ProM${PROM_MAJOR_VERSION}${PROM_MINOR_VERSION}_lib"

@Field final String SCRIPT_REQUEST_PREFIX = 'script='
@Field final String INIT_SCRIPT = "init${DEFAULT_SCRIPT_EXT}"
@Field final String TMP_DIR = '/tmp'

@Field final Class[] ITERABLE_CLASSES = [Collection, Object[]]
@Field final Class[] SIMPLE_CLASSES = [String, Number, String[], Number[]]
@Field final String[] EMPTY_STRING_ARRAY = new String[0]
@Field final String[] SCRIPT_SEARCH_PATHS = ["${PROM_HOME}/scripts", TMP_DIR]

// based on http://svn.apache.org/repos/asf/httpd/httpd/trunk/docs/conf/mime.types
@Field final Map<String, String> EXT_MIME_TYPES = [
    'avi': 'video/x-msvideo',
    'csv': 'text/csv',
    'html': 'text/html',
    'htm':'text/html',
    'json': DEFAULT_CONTENT_TYPE,
    'pdf': 'application/pdf',
    'png': 'image/png',
    'pnml': 'application/xml',
    'jpeg': 'image/jpeg',
    'jpg': 'image/jpeg',
    'jpe': 'image/jpeg',
    'svg': 'image/svg+xml',
    'svgz': 'image/svg+xml',
    'xml': 'application/xml',
] as Map<String, String>

// TODO stream result and output files to Redis
@Field final Cache<String, String> promResultCache = Caffeine.newBuilder()
    .expireAfterWrite(PROM_RESULT_EXPIRE_MINUTE, TimeUnit.MINUTES)
    .maximumSize(PROM_RESULT_CACHE_SIZE)
    .removalListener([ onRemoval: { key, value, cause ->
        File dir = new File("${TMP_DIR}/${key}")
        if (dir.exists()) {
            dir.deleteDir()
        }
        println("* Cached result for request '${key}' was removed due to ${cause}")
    } ] as RemovalListener)
    .build()

@Field final GroovyClassLoader promClassLoader = new GroovyClassLoader()

@Field final ExecutorService promWorkers = Executors.newFixedThreadPool(PROM_WORKER_THREADS)

// IMPORTANT: load jars first or init.groovy won't work
[PROM_DIST_DIR, PROM_LIB_DIR].each { dir ->
    new File(dir).listFiles().each {
        if (it.name.endsWith('.jar')) {
            promClassLoader.addURL(it.toURI().toURL())
        }
    }
}

System.setProperty('java.library.path', PROM_LIB_DIR)

boolean isIterable(object) {    
    return ITERABLE_CLASSES.any { it.isAssignableFrom(object?.class ?: Object) }
}

def sendResponse(exchange, code, message = '') {
    if (code == 200) {
        exchange.responseHeaders[HEADER_CONTENT_TYPE] = DEFAULT_CONTENT_TYPE
    }

    exchange.sendResponseHeaders(code, message ? message.length() : 0)
    exchange.getResponseBody().withWriter(DEFAULT_ENCODING) {
        it.write(message)
    }

    return true
}

def sendStaticFileAsResponse(exchange, f) {
    if (f instanceof File && f.exists() && f.isFile()) {
        exchange.responseHeaders[HEADER_CONTENT_TYPE] = EXT_MIME_TYPES["${f.name.tokenize('.')[-1]}"] ?: DEFAULT_MIME_TYPE

        byte[] bytes = f.bytes

        exchange.sendResponseHeaders(200, bytes.length)
        exchange.getResponseBody().setBytes(bytes)
    } else {
        sendResponse(exchange, 404, ERROR_NO_OUTPUT_FILE)
    }

    return true
}

def sendFileAsResponse(exchange, requestId, file) {
    File f = new File("${TMP_DIR}/${requestId}/${normalizeFileName(file)}")

    exchange.responseHeaders[HEADER_REQUEST_ID] = requestId
    return sendStaticFileAsResponse(exchange, f)
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

def normalizeObject(object) {
    def result = object

    if (object instanceof Closure) {
        result = normalizeObject(object.call())
    }

    return result
}

def normalizeFileName(file, defaultExt = '') {
    def fileName = file instanceof File ? file.name : normalizeObject(file)?.toString()

    def normalizedName = '';

    if (fileName) {
        normalizedName = fileName.tokenize('/')[-1].toLowerCase()
        normalizedName = URLEncoder.encode(normalizedName)
    }

    if (defaultExt && normalizedName.indexOf('.') < 0) {
        normalizedName = "${normalizedName}${defaultExt}"
    }

    return normalizedName;
}

def simpleValue(object) {    
    return SIMPLE_CLASSES.any { it.isAssignableFrom(object?.class ?: Object) } ? object : null
}

def toJsonString(object) {
    return new groovy.json.JsonBuilder(object).toString()
}

@Memoized
def loadScriptClass(String scriptFileName) {
    return promClassLoader.parseClass(new File(scriptFileName))
}

def loadScript(String scriptFileName, Binding context) {
    return loadScriptClass(scriptFileName).newInstance(context);
}

def runScript(scriptFileName, requestParams = [:], asJsonString = true) {
    File file = null;

    if (scriptFileName?.startsWith('/')) { // you're on your own
        file = new File(scriptFileName)
    } else {
        scriptFileName = normalizeFileName(scriptFileName, DEFAULT_SCRIPT_EXT)
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

    println("* Running script [${scriptFileName}]...")

    def expireUtcDateTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(PROM_RESULT_EXPIRE_MINUTE)
    def requestId = UUID.randomUUID().toString()

    if (!requestParams instanceof Map) {
        requestParams = [:]
    }

    // by default let's run in synchronized mode
    boolean asyncMode = requestParams.async?.toBoolean() == true
    // by default we'll need to setup context before running script for convenience
    // however, this will cost a few more seconds for each request...
    boolean requireContext = requestParams.context?.toBoolean() != false

    // prepare result object and directory for output files
    def result = [ finished: false, files: [] ]
    String outputDir = "${TMP_DIR}/${requestId}"
    new File(outputDir).mkdirs()
    promResultCache.put(requestId, result)

    // FIXME make bindings immutable
    def binding = new Binding([
        PROM_HOME: PROM_HOME,
        PROM_REQUEST_ID: requestId,
        PROM_REQUEST_PARAMS: requestParams,
        newFile: { fileName ->
            File f = new File("${TMP_DIR}/${requestId}/${normalizeFileName(fileName)}")
            result.files.add("${normalizeFileName(f)}")
            return f
        },
        addFile: { f ->
            result.files.add("${normalizeFileName(f)}")
        },
        removeFile: { f ->
            String fileName = "${normalizeFileName(f)}"
            result.files.remove(fileName)
        }
    ]);

    def exec = {
        // TODO needs an object pool for GroovyShell
        def shell = new GroovyShell(new GroovyClassLoader(promClassLoader), binding)
    
        if (requireContext) {
            loadScript(INIT_SCRIPT, binding).run()
        }

        result.result = simpleValue(shell.run(file, EMPTY_STRING_ARRAY))

        result.finished = true
    }

    if (asyncMode) {
        promWorkers.execute(exec as Runnable)
    } else {
        exec.call()
    }

    def finalResult = [
        id: requestId,
        script: scriptFileName,
        finished: result.finished,
        expireAt: "${expireUtcDateTime}",
        result: result.result,
        files: result.files
    ]

    return asJsonString ? toJsonString(finalResult) : finalResult
}

HttpServer server = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), PROM_BACKLOG_SIZE);

server.setExecutor(Executors.newFixedThreadPool(PROM_HTTPD_THREADS))

/**
 * This defines how we'll handle request made to '/script' context.
 * You can use either GET to call an existing script or POST custom script to execute.
 *
 * The following parameters in URL are supported at this point:
 * 1) file - script file name(without preceding path), we'll search in '$PROM_HOME/scripts' and then '/tmp'
 * 2) context - true if you need context being built before calling your script, defaults to true
 * 3) async - true if you want to execute the script in async mode, defaults to false
 * 4) showLastOutputFile - true if you want to show last output file instead of JSON result, defaults to false
 */
def scriptHandler = { exchange, params ->
    String scriptFileName = params.file

    if (exchange.requestMethod == HTTP_GET_METHOD) {
        if (!scriptFileName) {
            return sendResponse(exchange, 400, ERROR_NO_SCRIPT)
        }
    } else if (exchange.requestMethod == HTTP_POST_METHOD) {
        scriptFileName = scriptFileName 
            ? normalizeFileName(scriptFileName, DEFAULT_SCRIPT_EXT)
            : "generated-script-t${Thread.currentThread().id}${DEFAULT_SCRIPT_EXT}"
        scriptFileName = "${TMP_DIR}/${normalizeFileName(scriptFileName)}"

        new File(scriptFileName).withWriter(DEFAULT_ENCODING) { w ->
            String scriptContent = exchange.requestBody.text

            if (scriptContent.startsWith(SCRIPT_REQUEST_PREFIX)) {
                scriptContent = URLDecoder.decode(scriptContent.substring(SCRIPT_REQUEST_PREFIX.length()))
            }

            w.writeLine(scriptContent)
        }
    } else {
        return false
    }

    boolean showLastOutputFile = params.showLastOutputFile?.toBoolean() == true

    def rawResult = runScript(scriptFileName, params, false)

    // insteada of showing the json result, show the last output file
    return (showLastOutputFile && rawResult.finished == true && rawResult.files)
        ? sendFileAsResponse(exchange, rawResult.id, rawResult.files[-1])
        : sendResponse(exchange, 200, toJsonString(rawResult))
}

def statusHandler = { exchange, params ->
    boolean processed = false

    if (!params.id) {
        processed = sendResponse(exchange, 400, ERROR_NO_REQUEST_ID)
    } else if (exchange.requestMethod == HTTP_GET_METHOD) {
        def result = promResultCache.getIfPresent(params.id)

        processed = result == null
            ? sendResponse(exchange, 404, ERROR_NO_RESULT)
            : sendResponse(exchange, 200, toJsonString([
                finished: result.finished == true]))
    }

    return processed
}

def resultHandler = { exchange, params ->
    boolean processed = false

    if (!params.id) {
        processed = sendResponse(exchange, 400, ERROR_NO_REQUEST_ID)
    } else if (exchange.requestMethod == HTTP_GET_METHOD) {
        def result = promResultCache.getIfPresent(params.id)

        if (result == null) {
            processed = sendResponse(exchange, 404, ERROR_NO_RESULT)
        } else if (result.finished != true) {
            processed = sendResponse(exchange, 403, ERROR_UNFINISHED_REQUEST)    
        } else {
            processed = params.file
                ? sendFileAsResponse(exchange, params.id, params.file)
                : sendResponse(exchange, 200, toJsonString([
                    id: params.id,
                    finished: result.finished,
                    result: result.result,
                    files: result.files]))
        }
    }

    return processed
}

def rootHandler = { exchange, params ->
    // TODO CROS?

    return sendStaticFileAsResponse(exchange, new File(HTTP_WELCOME_PAGE))
}

// TODO Semantic URL? Maybe not since they will run behind GraphQL
[
    "${PROM_WEB_CONTEXT}/script" : scriptHandler, // run groovy script
    "${PROM_WEB_CONTEXT}/status" : statusHandler, // check request status(finished or not)
    "${PROM_WEB_CONTEXT}/result" : resultHandler, // get result(in JSON) or specific output file of given request
    "${PROM_WEB_CONTEXT}/": rootHandler
].each { k, v ->
    server.createContext(k, [ handle: { exchange ->
        try {
            def params = extractParameters(exchange.requestURI.query)

            if(v.call(exchange, params) != true) {
                sendResponse(exchange, 400, ERROR_NOT_SUPPORTED)
            }
        } catch(Exception e) {
            e.printStackTrace()
            // TODO polish detailed exception before sending back to client
            sendResponse(exchange, 500, "${ERROR_SOMETHING_WRONG}")
        }
    } ] as HttpHandler)
}

// initialize ProM before accepting any request
loadScript(INIT_SCRIPT, new Binding([
    PROM_HOME: PROM_HOME,
    PROM_DIST_DIR: PROM_DIST_DIR,
    PROM_LIB_DIR: PROM_LIB_DIR,
    PROM_CLASS_LOADER: promClassLoader,
])).run()

println "Starting server... open your browser and navigate to http://localhost:${DEFAULT_PORT}${PROM_WEB_CONTEXT}/..."

server.start()