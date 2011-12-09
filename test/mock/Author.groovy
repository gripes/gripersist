package mock

import net.sf.gripes.entity.annotation.*

@Entity class Author {
	String name
	Post posts
	
	def mappings = {
		many(posts)
	}
}
