package net.sf.gripes.entity.transform

import java.lang.annotation.Annotation
import java.lang.reflect.Field

import org.junit.Test

class TestEntityASTTransformation extends GroovyTestCase {

	@Test public void testAnnotationPresent() {		
		def bookFile = new File('test/mock/Author.groovy')
		
		assert bookFile.exists()

		GroovyClassLoader invoker = new GroovyClassLoader()		
		def cls2 = invoker.parseClass(bookFile)
		def book = cls2.newInstance()
		
		assertNotNull book.class.getAnnotation(javax.persistence.Entity)
		assertNotNull book.class.getField("posts").getAnnotation(javax.persistence.OneToMany)
	}
}