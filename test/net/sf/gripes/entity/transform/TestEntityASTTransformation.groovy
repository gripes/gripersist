package net.sf.gripes.entity.transform

import java.lang.annotation.Annotation
import java.lang.reflect.Field

import org.junit.Test

class TestEntityASTTransformation extends GroovyTestCase {

	@Test public void testAnnotationPresent() {		
		def bookFile = new File('test/mock/Book.groovy')
		assert bookFile.exists()

		GroovyClassLoader invoker = new GroovyClassLoader()		
		def cls2 = invoker.parseClass(bookFile)
		def book = cls2.newInstance()
		
		boolean found = false
		
		println book.class.annotations
		book.class.getFields().each { Field f ->
			println f.type.toString() + " - " + f.name + " -> " + f.annotations
		}
	}
}