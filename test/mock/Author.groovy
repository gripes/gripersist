package mock

import net.sf.gripes.entity.annotation.*

//import javax.persistence.Entity

class Author {
	@HasMany public Post posts
	
	def mappings = {
		many(posts)
	}
}
