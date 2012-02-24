package net.sf.gripes.util

import javax.servlet.ServletContext
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GripersistContextListener implements ServletContextListener {
	Logger logger = LoggerFactory.getLogger(GripersistContextListener.class)
	
	ServletContext servletContext
	
	@Override void contextInitialized(ServletContextEvent contextEvent) {
		servletContext = contextEvent.servletContext
		
		def dbConfig
		try { 
			def dbConfigFile = new File(this.class.classLoader.getResource("DB.groovy").getFile())
			if (dbConfigFile.exists()) {
				dbConfig = new ConfigSlurper().parse(dbConfigFile.toURI().toURL())
			}
		} catch (e) {
			e.printStackTrace() 
		}
		servletContext.setAttribute "gripes.config.db", dbConfig
	}

	@Override void contextDestroyed(ServletContextEvent contextEvent) {
		servletContext = contextEvent.servletContext
	}
}