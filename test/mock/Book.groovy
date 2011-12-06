package mock


@net.sf.gripes.entity.annotation.Entity class Book {
	public String name
	
	static mappings = {
		many(name)
	}
}
