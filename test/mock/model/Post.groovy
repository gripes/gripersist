package mock.model

import javax.persistence.GeneratedValue
import javax.persistence.Id

import net.sf.gripes.entity.annotation.*

@Entity class Post {
	@Id @GeneratedValue Long id
	
	String name
}
