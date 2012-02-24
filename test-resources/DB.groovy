database {
	gripersistOverridePU {
		schema = "create-drop"
		dialect = "org.hibernate.dialect.HSQLDialect"
		driver = "org.hsqldb.jdbc.JDBCDriver"
		url = "jdbc:hsqldb:mem:gripersist"
		user = "sa"
		password = ""
	
//		classes = "auto"
		classes = ["mock.model.Author","mock.model.Post"]
	}
}