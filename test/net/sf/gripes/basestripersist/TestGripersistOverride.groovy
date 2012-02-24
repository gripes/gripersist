package net.sf.gripes.basestripersist

import org.junit.Test;

import static org.junit.Assert.*

import javax.servlet.ServletContext

import net.sf.gripes.BaseTestCase
import net.sf.gripes.stripersist.Gripersist
import net.sourceforge.stripes.controller.DispatcherServlet
import net.sourceforge.stripes.controller.StripesFilter
import net.sourceforge.stripes.mock.MockServletContext

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TestGripersistOverride extends BaseTestCase {
	Logger logger = LoggerFactory.getLogger(TestGripersistOverride.class)	
	static Logger _logger = LoggerFactory.getLogger(TestGripersistOverride.class)
	static Map overrideAttributes = [:] //['gripes.persistence.xml':new File("test-resources/META-INF/persistence.xml").toURI().toURL()]
	
	static def oldGetAttribute
	@BeforeClass static void setupThisTestClassOverride() {
		_logger.debug "Setting up the test class"
		
		def cfg = new ConfigSlurper().parse(new File("test-resources/Config.groovy").toURI().toURL())
		def cfgDB = new ConfigSlurper().parse(new File("test-resources/DB.groovy").toURI().toURL())
		cfgDB.disabled = false
		Map overrideAttributes = [
			"gripes.config" : cfg
		   ,"gripes.config.db" : cfgDB
		]
		
		mockServletContext = createMockContext(null, overrideAttributes)
		Gripersist.requestInit()
	}
	@AfterClass static void teardownThiTestClass() {
		Gripersist.requestComplete()
		ServletContext.class.metaClass.getAttribute = oldGetAttribute
	}
	
	@Test void overridePersistenceXml() {
		assertNotNull Gripersist.getEntityManager("gripersistOverridePU")
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
	
	private static void createMockContext() {
		createMockContext([:],[:])
	}
}