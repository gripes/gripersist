package net.sf.gripes.entity.transform

import java.lang.annotation.Annotation
import java.lang.reflect.Field

import org.junit.Test

class TestEntityMappingASTTransformation extends GroovyTestCase {

	@Test public void testAnnotationPresent() {		
		def authorFile = new File('test/mock/Author.groovy')
		def postFile = new File('test/mock/Post.groovy')
		assert authorFile.exists()
		assert postFile.exists()

		GroovyClassLoader invoker = new GroovyClassLoader()
		def cls = invoker.parseClass(authorFile)
		def cls2 = invoker.parseClass(postFile)
		def author = cls.newInstance()
		
		boolean found = false
		author.class.getFields().each { Field f ->
			println f.type.toString() + " - " + f.name + " -> " + f.annotations
		}
	}
}