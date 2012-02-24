package net.sf.gripes.basestripersist

import static org.junit.Assert.*

import javax.servlet.ServletContext

import net.sf.gripes.BaseTestCase
import net.sf.gripes.stripersist.Gripersist
import net.sourceforge.stripes.controller.DispatcherServlet
import net.sourceforge.stripes.controller.StripesFilter
import net.sourceforge.stripes.mock.MockHttpServletResponse
import net.sourceforge.stripes.mock.MockServletContext

import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TestGripersist extends BaseTestCase {	
	Logger logger = LoggerFactory.getLogger(TestGripersist.class)
	static Logger _logger = LoggerFactory.getLogger(TestGripersist.class)
	
	@BeforeClass static void setupThisTestClass() {
		_logger.debug "Setting up the test class"
		def cfg = new ConfigSlurper().parse(new File("test-resources/Config.groovy").toURI().toURL())
		def cfgDB = new ConfigSlurper().parse(new File("test-resources/DB.groovy").toURI().toURL())
		cfgDB.disabled = true
		
		Map overrideAttributes = [
			"gripes.config" : cfg
		   ,"gripes.config.db" : cfgDB
		]
		
		mockServletContext = createMockContext(null, overrideAttributes)
		Gripersist.requestInit()
	}
	@AfterClass static void teardownThiTestClass() {
		Gripersist.requestComplete()
	}
	
	@Test void stripersistInitialized() {
		assertNotNull Gripersist.threadEntityManagers
	}
	
	@Test void haveEntityManagerFactories() {
		assertTrue(Gripersist.entityManagerFactories.size() > 0)
	}
	
	@Test void haveEntityManager() {
		assertNotNull Gripersist.getEntityManager()
	}
	
	@Test void haveGripersistEntityManager() {
		assertNotNull Gripersist.getEntityManager("gripersistTestPU")
	}
	
	private void requestActionBean(String name) {
		mockHttpResponse = new MockHttpServletResponse()
		mockServletContext.acceptRequest(mockRequest("/stripersist/${name}"), mockHttpResponse)
	}
	
	private static MockServletContext createMockContext(def newparams, def newattributes) {
		MockServletContext newMockServletContext = new MockServletContext(contextPath?:"");
		newattributes.each { String key, def val ->
			println "Setting attr: $key -> $val"
			newMockServletContext.setAttribute(key, val)
		}
		newMockServletContext.addFilter(StripesFilter.class,"StripesFilter", params+(newparams?:[:]))
		newMockServletContext.setServlet(DispatcherServlet.class,"DispatcherServlet",null)
		newMockServletContext
	}
	
	private static MockServletContext createMockContext() {
		createMockContext([:],[:])
	}
	
	@Override @Before void setupBaseTest() { } 
	@Override @After void teardownBaseTest() { }
}