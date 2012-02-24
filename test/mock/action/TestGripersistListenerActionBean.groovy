package mock.action

import static org.junit.Assert.*
import net.sf.gripes.basestripersist.Stripersist
import net.sourceforge.stripes.action.ActionBean
import net.sourceforge.stripes.action.ActionBeanContext
import net.sourceforge.stripes.action.HandlesEvent
import net.sourceforge.stripes.action.Resolution
import net.sourceforge.stripes.action.UrlBinding

@UrlBinding("/listener/{\$event}")
class TestGripersistListenerActionBean implements ActionBean {
	
	ActionBeanContext context
	@Override void setContext(ActionBeanContext context) { this.context = context }
	@Override ActionBeanContext getContext() { context }
	
	@HandlesEvent("initialized") Resolution overridePersistenceXml() {
		println "IT"
		
		null
	}
}
