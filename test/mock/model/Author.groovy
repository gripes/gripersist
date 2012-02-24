package mock.model

import javax.persistence.GeneratedValue
import javax.persistence.Id

import net.sf.gripes.entity.annotation.*

@Entity class Author {
	@Id @GeneratedValue Long id
	
	public String name
	public Post posts

	def mappings = {
		many(posts)
	}
}
