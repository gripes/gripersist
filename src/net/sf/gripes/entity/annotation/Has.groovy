package net.sf.gripes.entity.annotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.codehaus.groovy.transform.GroovyASTTransformationClass

@Target([ElementType.FIELD])
@Retention(RetentionPolicy.SOURCE)
@GroovyASTTransformationClass(["net.sf.gripes.entity.transform.EntityMappingASTTransformation"])
public @interface Has {
	public Class many();
	
	public Class one(); 
}
