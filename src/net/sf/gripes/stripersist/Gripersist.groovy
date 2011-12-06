package net.sf.gripes.stripersist

import org.stripesstuff.stripersist.Stripersist

import net.sourceforge.stripes.config.ConfigurableComponent
import net.sourceforge.stripes.controller.Intercepts
import net.sourceforge.stripes.controller.LifecycleStage
import net.sourceforge.stripes.controller.Interceptor

import javax.persistence.EntityManagerFactory
import javax.persistence.EntityManager;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Intercepts([LifecycleStage.RequestInit, LifecycleStage.RequestComplete])
class Gripersist extends Stripersist implements Interceptor, ConfigurableComponent {
	Logger logger = LoggerFactory.getLogger(Gripersist.class)
	static Logger _logger= LoggerFactory.getLogger(Gripersist.class)
	
    static {
        Package pkg = Stripersist.class.getPackage();
        _logger.info("""
##################################################
# Stripersist Version: 1.0.3, Build: 151:153
# Gripersist Version: 0.1.1
##################################################""")
    }

	static def getEntityClasses() {		
		def entityClasses = []
		Stripersist.entityManagerFactories.values().iterator().each { factory ->
			factory.metamodel.entities.each { 
				entityClasses.add it
			}
		}
		entityClasses.unique { a,b -> a.javaType.name <=> b.javaType.name }
	}

    static EntityManager getEntityManager() {
		_logger.debug "Searching for the EntityManager..."
		
		def dbConfigResource = this.classLoader.getResource("DB.groovy")
        if (Stripersist.entityManagerFactories.size() != 1 && dbConfigResource) {
			def dbConfig = new ConfigSlurper().parse(dbConfigResource.text)
			def primary = dbConfig.database.find{k,v->v.containsKey("primary")}?:dbConfig.database.find{it!=null}
			def key = primary.key
			
			_logger.debug "Using PersistenceUnit ${key} as the Default, can be overidden using 'primary=true' in DB.groovy"
			
	        getEntityManager(key)
        } else {
			_logger.debug "There is only one PersistenceUnit, using that."
			
			Stripersist.getEntityManager(Stripersist.entityManagerFactories.values().iterator().next())
		}
    }

	static EntityManagerFactory getEntityManagerFactory() {
		
	}
}