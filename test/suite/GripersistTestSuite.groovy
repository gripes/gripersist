package suite

import junit.framework.TestSuite

class GripersistTestSuite {
	private static final String TEST_ROOT = "test/net/sf/gripes/";
	
	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();
		GroovyTestSuite gsuite = new GroovyTestSuite();
		
		suite.addTestSuite(gsuite.compile(TEST_ROOT + "entity/transform/TestEntityMappingASTTransformation.groovy"));
		
		return suite;
	}
}
