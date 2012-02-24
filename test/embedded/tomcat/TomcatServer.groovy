package embedded.tomcat

import org.apache.catalina.Engine
import org.apache.catalina.Host
import org.apache.catalina.LifecycleException
import org.apache.catalina.connector.Connector
import org.apache.catalina.core.StandardContext
import org.apache.catalina.loader.WebappLoader
import org.apache.catalina.startup.Embedded
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class TomcatServer {
    Embedded embedded
	
	static String appBase = 'test/mock/webapps'
	static boolean shutdownHook = true
	static int port = 8888
	static Map<String, Object> initParams = [:]

    boolean isRunning
	
	StandardContext rootContext
	Map<String,StandardContext> contexts = [:]

    private static final Logger LOG = LoggerFactory.getLogger(TomcatServer.class);
    private static final boolean isInfo = LOG.isInfoEnabled();

	public TomcatServer() {
		this(port, appBase, shutdownHook, initParams)
	}
		
	/**
	 * Create a new Tomcat embedded server instance. Setup looks like:
	 * <pre>&lt;Server>
	 *    &lt;Service>
	 *        &lt;Connector />
	 *        &lt;Engine>
	 *            &lt;Host>
	 *                &lt;Context />
	 *            &lt;/Host>
	 *        &lt;/Engine>
	 *    &lt;/Service>
	 *&lt;/Server></pre>
	 *
	 * &lt;Server> & &lt;Service> will be created automcatically. We need to hook the remaining to an {@link Embedded} instance
	 * @param contextPath Context path for the application
	 * @param port Port number to be used for the embedded Tomcat server
	 * @param appBase Path to the Application files (for Maven based web apps, in general: <code>/src/main/</code>)
	 * @param shutdownHook If true, registers a server' shutdown hook with JVM. This is useful to shutdown the server
	 *                      in erroneous cases.
	 * @throws Exception
	 */
    public TomcatServer(int port, String appBase, boolean shutdownHook, Map params) {
        this.port = port
				
        embedded  = new Embedded()
        embedded.setName("embedded")
		
        Host localHost = embedded.createHost("localhost", appBase)		
        localHost.setAutoDeploy(false)
		
		WebappLoader loader = new WebappLoader(this.class.classLoader)
		loader.addRepository(new File("build/classes").toURI().toURL().toString());
		
        File webXml = new File('./test/embedded/tomcat/conf/web.xml')
		new File('./test/mock/webapps').listFiles().findAll{File f -> 
			f.isDirectory()
		}.each { File f ->
			StandardContext newCtx = (StandardContext) embedded.createContext("/${f.name}", "${f.name}")
			newCtx.setDefaultWebXml(webXml.canonicalPath)
			newCtx.setWorkDir("build/embeddedTomcat/work/catalina/localhost/${f.name}")
			params.each { k, v ->
				newCtx.addParameter(k,v)
			}			
			localHost.addChild(newCtx)
			
			contexts["${f.name}"] = newCtx
		}
        Engine engine = embedded.createEngine()
        engine.setDefaultHost(localHost.getName())
        engine.setName("catalina")
        engine.addChild(localHost)

        embedded.addEngine(engine)

        Connector connector = embedded.createConnector(localHost.getName(), port, false)
        embedded.addConnector(connector)

        // register shutdown hook
        if(shutdownHook) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    if(isRunning) {
                        if(isInfo) LOG.info("Stopping the Tomcat server, through shutdown hook");
                        try {
                            if (embedded != null) {
                                embedded.stop();
                            }
                        } catch (LifecycleException e) {
                            LOG.error("Error while stopping the Tomcat server, through shutdown hook", e);
                        }
                    }
                }
            });
        }
    }
	
	public def requestPage(String url) {
		URL reqURL = "http://localhost:${port}${url}".toURI().toURL()
		println "Requesting Page: ${reqURL}"
		def reqConn = reqURL.openConnection()
		
		["contentType","contentLength","headerFields","content"].collectEntries {
			try {
				new MapEntry(it, reqConn."${it}")
			} catch (e) {
				new MapEntry(it, "")
			}
		}
	}

    /**
     * Start the tomcat embedded server
     */
    public void start() throws LifecycleException {	
        if(isRunning) {
            LOG.warn("Tomcat server is already running @ port={}; ignoring the start", port)
            return
        }
		
        if(isInfo) {
			LOG.info("Starting the Tomcat server @ port={}", port)
        }

        embedded.setAwait(true)
        embedded.start()
		
        isRunning = true
    }

    /**
     * Stop the tomcat embedded server
     */
    public void stop() throws LifecycleException {
        if(!isRunning) {
            LOG.warn("Tomcat server is not running @ port={}", port)
            return
        }

        if(isInfo) LOG.info("Stopping the Tomcat server")

        embedded.stop()
        isRunning = false
    }

    public boolean isRunning() {
        return isRunning;
    }
}