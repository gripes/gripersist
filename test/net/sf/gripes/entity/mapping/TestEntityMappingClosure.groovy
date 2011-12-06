package net.sf.gripes.entity.mapping

import java.lang.annotation.Annotation
import java.lang.reflect.Field

import org.junit.Test

class TestEntityMappingClosure extends GroovyTestCase {

	@Test public void testMappingClosure() {		
		def bookFile = new File('test/mock/Book.groovy')
		def postFile = new File('test/mock/Post.groovy')
		
		assert bookFile.exists()
		assert postFile.exists()

		GroovyClassLoader invoker = new GroovyClassLoader()
		def cls = invoker.parseClass(bookFile)
		def cls2 = invoker.parseClass(postFile)
		def book = cls.newInstance()

		//Use delegated builder to find properties for the class 
		//many(name) --> many(String mock.Book.name)  NOT many(String Class.name)
		cls.metaClass.static.many = { args ->
			println "TEST: ${args}"
			"Mapped"
		}
		
		println book.mappings.call(1)
	}
}