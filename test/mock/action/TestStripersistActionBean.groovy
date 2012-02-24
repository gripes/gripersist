package mock.action

import static org.junit.Assert.*
import net.sf.gripes.basestripersist.Stripersist
import net.sourceforge.stripes.action.ActionBean
import net.sourceforge.stripes.action.ActionBeanContext
import net.sourceforge.stripes.action.HandlesEvent
import net.sourceforge.stripes.action.Resolution
import net.sourceforge.stripes.action.UrlBinding

@UrlBinding("/stripersist/{\$event}")
class TestStripersistActionBean implements ActionBean {
	
	ActionBeanContext context
	@Override void setContext(ActionBeanContext context) { this.context = context }
	@Override ActionBeanContext getContext() { context }
	
//	@HandlesEvent("stripersistInitialized") Resolution stripersistInitialized() {
//		assertNotNull Stripersist.threadEntityManagers
//	}
//	@HandlesEvent("haveEntityManagerFactories") Resolution haveEntityManagerFactories() {
//		assertTrue(Stripersist.entityManagerFactories.size() > 0)
//	}
//	@HandlesEvent("haveEntityManager") Resolution haveEntityManager() {
//		assertNotNull Stripersist.getEntityManager()
//	}
//	@HandlesEvent("haveGripersistEntityManager") Resolution haveGripersistEntityManager() {
//		assertNotNull Stripersist.getEntityManager("gripersistPU")
//	}
//	@HandlesEvent("overridePersistenceXml") Resolution overridePersistenceXml() {
//		assertNotNull Stripersist.getEntityManager("gripersistTestPU")
//	}
}
