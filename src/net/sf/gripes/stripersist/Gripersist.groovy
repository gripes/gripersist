package net.sf.gripes.stripersist

import groovy.util.slurpersupport.GPathResult

import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.util.Map
import java.util.Set
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.MappedSuperclass
import javax.persistence.Persistence
import javax.servlet.http.HttpServletRequest

import net.sourceforge.stripes.action.ActionBeanContext
import net.sourceforge.stripes.action.Resolution
import net.sourceforge.stripes.config.ConfigurableComponent
import net.sourceforge.stripes.config.Configuration
import net.sourceforge.stripes.controller.ExecutionContext
import net.sourceforge.stripes.controller.Interceptor
import net.sourceforge.stripes.controller.Intercepts
import net.sourceforge.stripes.controller.LifecycleStage
import net.sourceforge.stripes.controller.StripesConstants
import net.sourceforge.stripes.exception.StripesRuntimeException

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.stripesstuff.stripersist.StripersistInit


@Intercepts([LifecycleStage.RequestInit, LifecycleStage.RequestComplete])
class Gripersist implements Interceptor, ConfigurableComponent {
	Logger logger = LoggerFactory.getLogger(Gripersist.class)
	static Logger _logger = LoggerFactory.getLogger(Gripersist.class)

    /**
     * Parameter name for specifying StripersistInit classes in web.xml. This is optional;
     * StripersistInit classes are also loaded via the Extension.Packages.
     */
    public static final String INIT_CLASSES_PARAM_NAME = "StripersistInit.Classes";

    /**
     * Boolean initialization parameter that enables or disables automatic starting of transactions
     * with each request.
     */
    public static final String AUTOMATIC_TRANSACTIONS = "Stripersist.AutomaticTransactions";

    /**
     * Boolean initialization parameter that enables or disables automatic closing of entity manager
     * after each request.
     */
    public static final String DONT_CLOSE_ENTITYMANAGER = "Stripersist.DontCloseEntityManager";

    /**
     * Boolean initialization parameter that enables or disables rollback of active transactions
     * after each request.
     */
    public static final String DONT_ROLLBACK_TRANSACTION = "Stripersist.DontRollbackTransaction";

    private Configuration configuration;

    static private boolean automaticTransactions = true;
    static private boolean dontCloseEntityManager = false;
    static private boolean dontRollbackTransactions = false;

    static private final ThreadLocal<Map<EntityManagerFactory, EntityManager>> threadEntityManagers = new ThreadLocal<Map<EntityManagerFactory, EntityManager>>();
    static private final Map<String, EntityManagerFactory> entityManagerFactories = new ConcurrentHashMap<String, EntityManagerFactory>();
    static private final Map<Class<?>, EntityManagerFactory> entityManagerFactoryLookup = new ConcurrentHashMap<Class<?>, EntityManagerFactory>();
	
    static {
        Package pkg = Gripersist.class.getPackage();
        _logger.info("""
##################################################
# Stripersist Version: 1.0.3, Build: 151:153
# Gripersist Version: 0.1.1
##################################################""")
    }
	
	def gripesConfig
	def dbConfig
	public URL createFromTemplate(URL template) {
		File templateFile = new File(template.getFile().replaceAll("template", "xml"))
		File persistenceXml = File.createTempFile("persistence", "xml")
//		persistenceXml.createNewFile()
		persistenceXml.deleteOnExit()

		persistenceXml.text = """<persistence xmlns="http://java.sun.com/xml/ns/persistence"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://java.sun.com/xml/ns/persistence http://java.sun.com/xml/ns/persistence/persistence_1_0.xsd"
	version="1.0">
"""
		
		String templateText = ""
		dbConfig.database.each { String name, def options ->
			templateText = template.text
								.replaceAll(/\[NAME\]/,name)
								.replaceAll(/\[DBSCHEMA\]/,options.schema?:'')
								.replaceAll(/\[DBDIALECT\]/,options.dialect?:'')
								.replaceAll(/\[DBDRIVER\]/,options.driver?:'')
								.replaceAll(/\[DBURL\]/,options.url?:'')
								.replaceAll(/\[DBUSER\]/, options.user?:'')
								.replaceAll(/\[DBPASSWORD\]/, options.pass?:'')
			
			if(options.classes.equals("auto")) {
				templateText = templateText
									.replaceAll(/\[AUTO\]/,'<property name="hibernate.archive.autodetection" value="class"/>')
									.replaceAll(/\[CLASSES\]/,'')
			} else {
				templateText = templateText
									.replaceAll(/\[AUTO\]/,'')
									.replaceAll(/\[CLASSES\]/, options.classes.collect{"<class>$it</class>"}.join("\n"))
			}
			
			def addonConfig = ""
			gripesConfig.addons.each { addon ->
				if((addon=~/-src/).find()) {
					addonConfig = new File("gripes-addons/"+addon.replace("-src","")+"/gripes.addon")
				} else if(new File("addons/${addon}/gripes.addon").exists()) {
					addonConfig = new File("addons/${addon}/gripes.addon")
				} else {
					addonConfig = new File("../${addon}/gripes.addon")
				}
				
				def config = new ConfigSlurper().parse(addonConfig.text)
				templateText = templateText.replaceAll(/\[ADDITIONAL\]/,"[ADDITIONAL]"+((config.persistence.size()>0)?config.persistence:''))
			}
			templateText = templateText.replaceAll(/\[ADDITIONAL\]/,"")
			
			persistenceXml.text += templateText
		}
		
		persistenceXml.text +="\n</persistence>"
		
		String jarpath = templateFile.canonicalPath.replaceAll(/META-INF.*$/,'')
		
		File jarFile = new File(jarpath+"persistence-template.jar")
		jarFile.createNewFile()
//		jarFile.deleteOnExit()

		ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(jarpath+"persistence-template.jar"))
		zipFile.putNextEntry(new ZipEntry("META-INF/persistence.xml"))
		zipFile.setBytes(persistenceXml.bytes)
		zipFile.close()
		
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		try {
			Method method = sysclass.getDeclaredMethod("addURL", [java.net.URL] as Class[]);
			method.setAccessible(true);
			method.invoke(sysloader, jarFile.toURI().toURL());
		} catch (Throwable t) {
			t.printStackTrace();
			throw new IOException("Error, could not add URL to system classloader");
		}
		
		jarFile.toURI().toURL()
	}
	
    /**
     * Called by Stripes during initialization.
     */
    public void init(Configuration configuration) {
        this.configuration = configuration;
		println "INIT GRIPERSIST"
        try {
            // Just in case this is not the first call to this method, release any resources
            cleanup();
			
			URL url
			if (configuration.servletContext && configuration.servletContext.getAttribute('gripes.persistence.xml')) {
				url = configuration.servletContext.getAttribute('gripes.persistence.xml')
				logger.info("Using persistence.xml from gripes.persistence.xml: {}", url);
				
				init(url)
			} else {
				gripesConfig = configuration.servletContext.getAttribute("gripes.config")?:
								(new ConfigSlurper().parse(this.class.classLoader.getResource("Config.groovy")))								
				dbConfig = configuration.servletContext.getAttribute("gripes.config.db")?:
								(new ConfigSlurper().parse(this.class.classLoader.getResource("DB.groovy")))

				def xmlTempl =  this.class.classLoader.getResource("META-INF/persistence.template")
				println (dbConfig)
				println !dbConfig.disabled 
				println xmlTempl
				if(dbConfig && !dbConfig.disabled && xmlTempl) {
					println "DB ISN'T DISABLED"
					url = createFromTemplate(xmlTempl)
					
					init("jar:${url.toString()}!/META-INF/persistence.xml".toURL())
				} else {
					// try to get all available resources.
					Enumeration<URL> allResources = getClass().getClassLoader().getResources("META-INF/persistence.xml");				
					if (allResources != null && allResources.hasMoreElements()) {
						while (allResources.hasMoreElements()) {
							url = allResources.nextElement();
							logger.info("Reading persistence.xml from {}", url);
							init(url);
						}
					} else {
						logger.debug "Still looking for persistence.xml"
											
						url = Thread.currentThread().getContextClassLoader().getResource("/META-INF/persistence.xml");
	
						// url may be null if using ant/junit. if it is null we'll try a
						// different classloader - thanks freddy!
						if (url == null) {
							log.debug "Still no persistence.xml, one last chance..."
							url = getClass().getResource("/META-INF/persistence.xml");
						}
						
						log.debug("Reading persistence.xml from {}", url);
						init(url);
					}				
				}
			}

            automaticTransactions = getConfigurationSwitch(AUTOMATIC_TRANSACTIONS, automaticTransactions);

            logger.info("Automatic transactions {}", Gripersist.automaticTransactions ? "enabled" : "disabled");

            dontCloseEntityManager = getConfigurationSwitch(DONT_CLOSE_ENTITYMANAGER, dontCloseEntityManager);
            if (dontCloseEntityManager)
                logger.warn("EntityManagers will NOT be closed automatically. This is only intended to be used for unit testing.");

            dontRollbackTransactions = getConfigurationSwitch(DONT_ROLLBACK_TRANSACTION, dontRollbackTransactions);
            if (dontRollbackTransactions)
                logger.warn("Transactions will NOT be rolled back automatically. This is only intended to be used for unit testing.");

            requestInit();
            for (Class<? extends StripersistInit> initClass : configuration.getBootstrapPropertyResolver()
                    .getClassPropertyList(INIT_CLASSES_PARAM_NAME, StripersistInit.class)) {
                try {
                    if (!initClass.isInterface() && ((initClass.getModifiers() & Modifier.ABSTRACT) == 0)) {
                        logger.debug("Found StripersistInit class {} - instanciating and calling init()", initClass);
                        initClass.newInstance().init();
                    }
                } catch (Exception e) {
                    logger.error("{} Error occurred while calling init() on {}",e , initClass);
                }
            }
            requestComplete();
        } catch (Exception e) {
			e.printStackTrace()
            logger.error("1"+e);
        }
    }

    /**
     * Get a boolean configuration parameter from web.xml
     * 
     * @param name
     *            the name of the parameter
     * @param defaultValue
     *            the value if not set in web.xml
     * @return either the value in web.xml or the default value if not set in
     *         web.xml
     */
    private boolean getConfigurationSwitch(String name, boolean defaultValue) {
        String stringValue = configuration.getBootstrapPropertyResolver().getProperty(name);
        if (stringValue != null)
            return Boolean.valueOf(automaticTransactions);
        else
            return defaultValue;
    }

    /**
     * Initialize Stripersist, pulling persistent unit names from the specified
     * URL.
     * 
     * @param xml
     *            a URL pointing to persistence.xml
     */
    public void init(URL xml) {
        logger.debug("Initializing Stripersist using JPA persistence file....");
		
		String name = null
		String firstPersistentUnit = null;
		EntityManagerFactory factory
	 	Map<String, Object> configOverrides
        try {
            GPathResult xmlDoc = new XmlSlurper().parse(xml.newReader())
		   	xmlDoc["persistence-unit"].each { GPathResult persistenceUnit ->
			   	name = persistenceUnit."@name"
			   	firstPersistentUnit = firstPersistentUnit?:name
			   	logger.info("Creating EntityManagerFactory for persistence unit {}", name);
			   
			   	configOverrides = new HashMap<String, Object>();
			   	logger.info("Using {} and {}", name, configOverrides)
			   	factory = Persistence.createEntityManagerFactory(name, configOverrides);
			   
			   	Gripersist.entityManagerFactories.put(name, factory);
			   	logger.debug("created factory ", factory, " for ", name)//, factory, name);
			   	logger.debug("emf.get(" + name + ") = " + Gripersist.entityManagerFactories.get(name));
			   	logger.debug("emf = " + Gripersist.entityManagerFactories);
			   
			   	persistenceUnit.children().each { GPathResult child ->
				   	if ("class".equalsIgnoreCase(child.name())) {
					   	String className = child.text();
					   	try {
						   	Class<?> clazz = Class.forName(className);

						   	associateEntityManagerWithClass(factory, name, clazz);
					   	} catch (Exception e) {
					   		logger.error(" Exception occurred while loading " + className);
						   e.printStackTrace()
					   	}
				   	} else if ("jar-file".equalsIgnoreCase(child.name())) {
					   	String jarFile = child.text();

					   	if (jarFile.startsWith("../../lib/"))
							jarFile = jarFile.substring(10);

						for (Class<?> clazz : findEntities(jarFile)) {
						   associateEntityManagerWithClass(factory, name, clazz);
					   	}
				   	}
			   	}
			   
			   	//load up the Entities in the jar file that contained the persistence.xml file.
			   	Set<Class<?>> classes = new HashSet<Class<?>>();

			   	String urlPath = xml.getFile();
			   	logger.info("checking jar file from urlPath = " + urlPath);
			   	if ("vfszip".equals(xml.getProtocol())) {
				   	URL newUrl = new URL(xml.toString().substring(0, xml.toString().length() - 25));
				   	logger.info("getting entities from new url " + newUrl);
				   	classes.addAll(findEntitiesFromUrl(newUrl));
			   	} else {
				   	urlPath = URLDecoder.decode(urlPath, "UTF-8");
				   	if (urlPath.startsWith("file:")) urlPath = urlPath.substring(5);
				   	if (urlPath.endsWith("!/META-INF/persistence.xml")) urlPath = urlPath.substring(0, urlPath.length() - 26);

				   	File file = new File(urlPath);
				   	if (file.isDirectory())  
				   		classes.addAll(findEntitiesInDirectory("", file));
					else 
				   		classes.addAll(findEntitiesInJar(file));
			   }

			   classes.each { Class<?> clazz ->
				   associateEntityManagerWithClass(factory, name, clazz);
			   }
		   }

		   if (Gripersist.entityManagerFactoryLookup.size() == 0 && firstPersistentUnit != null) {
			   factory = Gripersist.entityManagerFactories.get(firstPersistentUnit);
			   name = firstPersistentUnit;

			   findEntities(null).each { Class<?> clazz ->
				   associateEntityManagerWithClass(factory, name, clazz);
			   }
		   }
        } catch (Throwable e) {
            logger.error(""+e);
			e.printStackTrace()
        }
    }
	
	static def getEntityClasses() {		
		def entityClasses = []
		Gripersist.entityManagerFactories.values().iterator().each { factory ->
			factory.metamodel.entities.each { 
				entityClasses.add it
			}
		}
		entityClasses.unique { a,b -> a.javaType.name <=> b.javaType.name }
	}

    static EntityManager getEntityManager() {
		_logger.debug "Searching for the EntityManager..."
		
		def dbConfigResource = this.classLoader.getResource("DB.groovy")
        if (Gripersist.entityManagerFactories.size() != 1 && dbConfigResource) {
			def dbConfig = new ConfigSlurper().parse(dbConfigResource.text)
			def primary = dbConfig.database.find{k,v->v.containsKey("primary")}?:dbConfig.database.find{it!=null}
			def key = primary.key
			
			_logger.debug "Using PersistenceUnit ${key} as the Default, can be overidden using 'primary=true' in DB.groovy"
			
	        getEntityManager(key)
        } else {
			_logger.debug "There is only one PersistenceUnit, using that."
			
			Gripersist.getEntityManager(Gripersist.entityManagerFactories.values().iterator().next())
		}
    }
	
	private void associateEntityManagerWithClass(EntityManagerFactory factory, String name, Class<?> clazz) {
		if (!Gripersist.entityManagerFactoryLookup.containsKey(clazz)) {
			logger.debug("Associating " + clazz.getName() + " with persistence unit \"" + name + "\"")
	
			Gripersist.entityManagerFactoryLookup.put(clazz, factory);
		}
	}
	
    /**
     * Finds and returns all classes that are annotated with {@link Entity} or
     * {@link MappedSuperclass}. This code was taken from Stripes
     * {@link net.sourceforge.stripes.util.ResolverUtil} and modified to suit
     * our needs.
     * 
     * @param jarName
     * @return a set of entity classes
     */
    protected static Set<Class<?>> findEntities(String jarName) {
        URLClassLoader loader = (URLClassLoader) Thread.currentThread().getContextClassLoader();

        URL[] urls = loader.getURLs();

        Set<Class<?>> classes = new HashSet<Class<?>>();

        for (URL url : urls) {
            try {
                String urlPath = url.getFile();
                urlPath = URLDecoder.decode(urlPath, "UTF-8");
                if (jarName != null && !urlPath.endsWith(jarName))
                    continue;
                // If it's a file in a directory, trim the stupid file: spec
                if (urlPath.startsWith("file:")) {
                    urlPath = urlPath.substring(5);
                }

                File file = new File(urlPath);

                if (jarName == null && file.isFile())
                    continue;

                _logger.debug("Scanning for entities in [" + urlPath + "]");
                if (file.isDirectory()) {
                    _logger.debug("checking directory {}", file);
                    classes.addAll(findEntitiesInDirectory("", file));
                } else {
                    _logger.debug("checking jar {}", file);
                    classes.addAll(findEntitiesInJar(file));
                }

            } catch (Exception e) {
                _logger.error("2"+e);
            }
        }

        return classes;
    }

    /**
     * Returns a set of classes that are annotated with {@link Entity} or
     * {@link MappedSuperclass} in the specified jar file.
     * 
     * @param file
     * @return a set of entity classes
     */
    private static Set<? extends Class<?>> findEntitiesInJar(File file) {
		if(!file.exists()) return (new HashSet<Class<?>>())
        try {
            JarEntry entry;
            JarInputStream jarStream = new JarInputStream(new FileInputStream(file));

            Set<Class<?>> classes = new HashSet<Class<?>>();

            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".class")) {
                    addIfEntity(classes, name);
                }
            }

            return classes
        } catch (IOException ioe) {
            _logger.error("Could not search jar file '" + file + "' for entities due to an IOException: " + ioe.getMessage());
        }

        return null
    }

    /**
     * Returns a set of classes that are annotated with {@link Entity} or
     * {@link MappedSuperclass} in the specified directory.
     * 
     * @param parent
     * @param location
     * @return a set of entities
     */
    private static Set<? extends Class<?>> findEntitiesInDirectory(String parent, File location) {
        File[] files = location.listFiles();
        StringBuilder builder = null;

        // File.listFiles() can return null when an IO error occurs!
        if (files == null) {
            _logger.warn("Could not list directory " + location.getAbsolutePath() + " when looking for entities");
            return null;
        }

        Set<Class<?>> classes = new HashSet<Class<?>>();

        for (File file : files) {
            builder = new StringBuilder(100);
            if (parent != null && parent.length() > 0)
                builder.append(parent).append("/");
            builder.append(file.getName());
            String packageOrClass = (parent == null ? file.getName() : builder.toString());

            if (file.isDirectory()) {
                classes.addAll(findEntitiesInDirectory(packageOrClass, file));
            } else if (file.getName().endsWith(".class")) {
                addIfEntity(classes, packageOrClass);
            }
        }

        return classes;
    }

    /**
     * Returns a set of classes that are annotated with {@link Entity} or
     * {@link MappedSuperclass} in the specified jar file.
     * 
     * @param file
     * @return a set of entity classes
     */
    private static Set<? extends Class<?>> findEntitiesFromUrl(URL url) {
        try {
            JarEntry entry;
            JarInputStream jarStream = new JarInputStream(url.openStream());

            Set<Class<?>> classes = new HashSet<Class<?>>();

            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".class")) {
                    addIfEntity(classes, name);
                }
            }

            return classes;
        } catch (IOException ioe) {
            _logger.error("Could not search URL '" + url + "' for entities due to an IOException: " + ioe.getMessage());
        }

        return new HashSet<Class<?>>();
    }

    /**
     * If fqn describes a class annotated with @Entity it will be added to the
     * classes @Set otherwise it is ignored.
     * 
     * @param classes
     *            the set that entity classes will be added to
     * @param fqn
     *            the fully qualified class name to check
     */
    private static void addIfEntity(Set<Class<?>> classes, String fqn) {
        try {
            String externalName = fqn.substring(0, fqn.indexOf('.')).replace('/', '.');

            Class<?> type = Thread.currentThread().getContextClassLoader().loadClass(externalName);
            if (type.getAnnotation(Entity.class) != null || type.getAnnotation(MappedSuperclass.class) != null) {
                classes.add(type);
            }
        } catch (NoClassDefFoundError e) {
            // Ignored
        } catch (Throwable t) {
            _logger.debug("Could not examine class '" + fqn + "'" +  " due to a " + t.getClass().getName() + " with message: " + t.getMessage());
        }
    }

    /** Remove and close all the entity manager factories and clear the entity manager lookup map. */
    protected void cleanup() {
        Gripersist.entityManagerFactoryLookup.clear();

        Iterator<EntityManagerFactory> iterator = Gripersist.entityManagerFactories.values().iterator();

        while (iterator.hasNext()) {
            EntityManagerFactory factory = iterator.next();
            if (factory.isOpen())
                factory.close();
            iterator.remove();
        }
    }

    /**
     * Shutdown the EntityManagerFactories so they can release resources
     */
    @Override
    protected void finalize() throws Throwable {
        cleanup();
    }

    /**
     * Finds the EntityManagerFactory which is associated with the specified
     * persistence unit. Normally you shouldn't use this class because
     * Stripersist won't clean up any EntityManagers that you create.
     * 
     * @param persistenceUnit
     *            the name of the persistence unit
     * @return an EntityManagerFactory or null
     */
    public static EntityManagerFactory getEntityManagerFactory(String persistenceUnit) {
        return Gripersist.entityManagerFactories.get(persistenceUnit);
    }

    /**
     * Finds the EntityManagerFactory which is associated with the specified
     * class. Normally you shouldn't use this class because Stripersist won't
     * clean up any EntityManagers that you create.
     * 
     * @param forType
     *            a class that the EntityManagerFactory knows how to handle
     * @return an EntityManagerFactory
     */
    public static EntityManagerFactory getEntityManagerFactory(Class<?> forType) {
        return Gripersist.entityManagerFactoryLookup.get(forType);
    }

    /**
     * Gets an EntityManager from the specified factory.
     * 
     * @param factory
     * @return an EntityManager or null
     */
    public static EntityManager getEntityManager(EntityManagerFactory factory) {
		_logger.debug "Getting single EntityManager with $factory"
        Map<EntityManagerFactory, EntityManager> map = threadEntityManagers.get();
        EntityManager entityManager = null;

        if (map == null) {
            StripesRuntimeException sre = new StripesRuntimeException(
                    "It looks like Stripersist isn't configured as an Interceptor\n"
                            + "or you're calling Stripersist from a thread outside of the\n"
                            + "StripesFilter. If you want use Stripersist from outside\n"
                            + "of Stripes you should call Stripersist.initRequest() inside\n"
                            + "of a try block before requesting an EntityManager and\n"
                            + "call Stripersist.requestComplete() in a finally block so\n"
                            + "Stripersist can clean everything up for you.");

            _logger.error(""+sre);

            return null;
        }

        entityManager = map.get(factory);

        if (entityManager == null) {
            entityManager = factory.createEntityManager();
            map.put(factory, entityManager);
        }

        if (automaticTransactions) {
            EntityTransaction transaction = entityManager.getTransaction();

            if (!transaction.isActive())
                transaction.begin();
        }

        return entityManager;
    }

    /**
     * Retrieves the EntityManager associated with the named persistence unit.
     * 
     * @param persistenceUnit
     *            the name of the persistence unit
     * @return an EntityManager or null
     */
    public static EntityManager getEntityManager(String persistenceUnit) {
		_logger.debug "Getting entity Manager by PU: $persistenceUnit"
        EntityManagerFactory factory = getEntityManagerFactory(persistenceUnit);

        if (factory == null) {
            _logger.warn("Couldn't find EntityManagerFactory for persistence unit {}", persistenceUnit);
            return null;
        }

        return getEntityManager(factory);
    }

    /**
     * Retrieves an EntityManager that may be used with the specified type.
     * 
     * @param forType
     *            a class that is handled by the EntityManager
     * @return an EntityManager or null
     */
    public static EntityManager getEntityManager(Class<?> forType) {
		_logger.debug "Getting entity Manager by class: $forType"
        _logger.debug("Looking up EntityManager for type {}", forType.getName());

        EntityManagerFactory entityManagerFactory = getEntityManagerFactory(forType);

        if (entityManagerFactory == null) {
            _logger.warn("Couldn't find EntityManagerFactory for class {}", forType.getName());
            return null;
        }

        return getEntityManager(entityManagerFactory);
    }

    /**
     * Initializes request specific variables. Under normal circumstances this
     * is called automatically but if you want to use Stripersist from your own
     * threads you may call this as long as you remember to call
     * {@link #requestComplete()} when you are done (preferably from inside a
     * finally block).
     */
    static void requestInit() {
        Map<EntityManagerFactory, EntityManager> map = threadEntityManagers.get();
        if (map == null) {
            map = new ConcurrentHashMap<EntityManagerFactory, EntityManager>();
            threadEntityManagers.set(map);
        }
    }

    /**
     * Rolls back current {@link EntityTransaction}s and closes
     * {@link EntityManager}s. Under normal circumstances this is called
     * automatically but if you've called {@link #requestInit()} from within
     * your own thread you should make sure this is in a finally block so
     * everything gets cleaned up.
     */
    public static void requestComplete() {
        Map<EntityManagerFactory, EntityManager> map = Gripersist.threadEntityManagers.get();
		
        // looks like nobody needed us this time
        if (map == null) return;

        _logger.trace("Cleaning up EntityManagers");

        Gripersist.threadEntityManagers.remove();

        for (EntityManager entityManager : map.values()) {
            EntityTransaction transaction = entityManager.getTransaction();

            if (transaction != null) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
            }

            if (entityManager.isOpen()) {
                entityManager.close();
            }
        }
    }

    /**
     * Called by Stripe's {@link Interceptor} system. We use it to perform
     * initialization and cleanup at the start and end of each request
     * respectively.
     */
    public Resolution intercept(ExecutionContext context) throws Exception {
        ActionBeanContext abc = context.getActionBeanContext();
        HttpServletRequest request = abc == null ? null : abc.getRequest();

        if (request == null || request.getAttribute(StripesConstants.REQ_ATTR_INCLUDE_PATH) == null) {
            switch (context.getLifecycleStage()) {
                case net.sourceforge.stripes.controller.LifecycleStage.RequestInit:
                    log.trace("RequestInit");
                    requestInit();
                    break;
				case net.sourceforge.stripes.controller.LifecycleStage.RequestComplete:
                    log.trace("RequestComplete");
                    requestComplete();
                    break;
			}
        }

        return context.proceed();
    }
}