package net.sf.gripes.util

import static org.junit.Assert.*
import net.sf.gripes.BaseTestCase

import org.apache.catalina.core.StandardContext
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import embedded.tomcat.TomcatServer

class TestGripersistContextListener extends BaseTestCase {

	static TomcatServer server
	
	@BeforeClass static void setupThisClass() {
		server = new TomcatServer(8888, 'test/mock/webapps', true, [:])
		server.start()
	}
	
	@AfterClass static void teardownThisClass() {
		server.stop()
	}
	
	@Test void testWithEmbedded() {
		assertNotNull server.contexts.listener.context
		assertNotNull server.contexts.listener.context.getAttribute("gripes.config.db")
	}
	
	@Override @Before void setupBaseTest() { }		
	@Override @After void teardownBaseTest() { }
}