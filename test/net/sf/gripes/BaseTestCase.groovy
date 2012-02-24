package net.sf.gripes

import java.util.Map

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

import net.sf.gripes.stripersist.Gripersist
import net.sourceforge.stripes.action.ActionBean
import net.sourceforge.stripes.action.ActionBeanContext
import net.sourceforge.stripes.mock.MockHttpServletRequest
import net.sourceforge.stripes.mock.MockHttpServletResponse
import net.sourceforge.stripes.mock.MockRequestDispatcher
import net.sourceforge.stripes.mock.MockServletContext

import org.junit.After
import org.junit.Before
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * @author clmarquart
 */
class BaseTestCase {
	Logger logger = LoggerFactory.getLogger(this.class)

	int status
	
	boolean stripersist = false
	
	String requestURI
	
	HttpServletRequest request
	HttpServletRequestWrapper requestWrapper
	HttpServletResponse response
	HttpSession session
	ServletContext servletContext
	ActionBean actionBean
	Map attributes
	Map headers
	def context
	
	static String contextPath = "/"
	static MockServletContext mockServletContext
	static MockHttpServletRequest mockHttpRequest
	static MockHttpServletResponse mockHttpResponse
	
	static Map params = [
		"ActionResolver.Packages"	: "mock",
	 	"Extension.Packages"		: "net.sf.gripes.stripersist"
	]	
	
	@Before void setupBaseTest() {
		createRequest("/")
		createResponse()
		createServletContext()
		createActionBeanContext("/", "/web", params)
		Gripersist.requestInit()
		loadConfigFile()
	}
	
	@After void teardownBaseTest() {
		Gripersist.requestComplete()
	}
	
	
	/**
	 * Creates the HttpServletRequest.
	 * 
	 * @param webRoot - Used so calls to getRealPath will return the correct java.io.File  
	 */
	public void createRequest(String webRoot) {			
		status = 200
		requestURI = "/"
		
		session = [
			setAttribute: {key, value -> attributes.put(key,value) },
			getAttribute: {attr->
				println "Getting $attr ."
				attributes."$attr"
			}
		] as HttpSession
		
		request = [
			setRequestURI: {uri-> requestURI = uri },
			setAttribute: {key, value -> attributes.put(key,value) },
			getRequestURI: { requestURI },
			getRealPath: {path->
				new File(".").canonicalPath+"/${webRoot?:'web'}/"
			},
			getServerName: { serverName },
			getLocale: { 
				Locale.US
			},
			getSession: { 
				session
			},
			getAttribute: {attr->
				println "Getting $attr ." 
				attributes."$attr"	
			},
			getContextPath: {
				"/"
			},
			getRequestDispatcher: {path ->
				println path
				new MockRequestDispatcher(path)	
			}
		] as HttpServletRequest		
	}
	
	/**
	 * Creates the HttpServletResponse.
	 */
	public void createResponse() {		
		response = [
			encodeRedirectURL: { String url ->
				url
			},
			getHeader: { String name ->
				headers.get(name)
			},
			setHeaders: { Map map ->
				map.each { String key, Object value ->
					headers.put(key, value)
				}
			},
			sendRedirect: { String loc ->
				URI locURL = loc.toURI()
				StringBuilder locBldr = new StringBuilder()
				
				if(!locURL.getScheme()) {
					if(loc.startsWith("/")) { 
						locBldr.append loc
					} else { }
				}
				
				setHeaders("Location": loc)
				setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY)
			},
			setStatus: { status = it }
		] as HttpServletResponse
	}
	
	public void createServletContext(String webRoot) {
		servletContext = [
			setAttribute: {key, value -> attributes.put(key,value) },
			getAttribute : {attr ->
				println "GETTING $attr"
				 
				attributes."$attr"
			},
			getRealPath: { path->
				new File(".").canonicalPath+"/${webRoot?:'WebRoot'}/"
			}
		] as ServletContext
	}
	
	public void createActionBeanContext(String contextPath, String webRoot, Map params) {	
		context = [
			getRequest : { request },
			getResponse : { response },
			getServletContext : { servletContext },
			getContextWebRoot : {
				new File(".").canonicalPath+"/${webRoot?:'WebRoot'}/"
			},
			getRealPath: {path->
				new File(".").canonicalPath+"/${webRoot?:'WebRoot'}/"
			}
		] as ActionBeanContext
	}
	
	public MockHttpServletRequest mockRequest(String path) {
		new MockHttpServletRequest("/",path)
	}
	
	public void loadConfigFile(String configToTest) {		
		def configSlurper = new ConfigSlurper()
		configSlurper.setBinding(["host":"host.local"])
		
		File configFile
		
		def tryOne = this.class.classLoader.getResource('Config.groovy')
		def tryTwo = new File(servletContext.getRealPath("/")+"WEB-INF/Config.groovy")
		
		if( tryOne && (new File(tryOne.getFile()).exists())) {
			logger.debug "Found Config.groovy on the classpath"
			configFile = new File(tryOne.getFile())
		} else if (tryTwo && tryTwo.exists()) {
			logger.debug "Using Config.groovy from WEB-INF"
			configFile = new File(servletContext.getRealPath("/")+"WEB-INF/Config.groovy")
		} else {
			logger.debug "Create a blank Config.groovy in 'java.io.tmpdir'"
			configFile = new File(System.getProperty("java.io.tmpdir")+'/Config.groovy')
			configFile.createNewFile()
			configFile.deleteOnExit()
		}
		def lines = configFile.readLines()
		lines[0] = "using=\"${configToTest}\""
		
		attributes = [
			"gripes.config" : configSlurper.parse(lines.join("\n"))
		]
	}
	
	public void teardownTestCase() {
		context = null
		response = null
		status = 0
	}
}
