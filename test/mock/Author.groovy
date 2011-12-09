package mock

import net.sf.gripes.entity.annotation.*

@Entity class Author {
	public String name
	public Post posts
	
	def mappings = {
		many(posts)
	}
}
