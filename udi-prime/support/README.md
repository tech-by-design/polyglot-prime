# Code Generation Utilities

The `support` directory contains utilities for generating code using different
ORM frameworks, specifically jOOQ and Hibernate. These code generators create
JAR files which can be used by services and applications.

## JooqCodegen

### Overview

`JooqCodegen` is a Java utility that generates Java classes based on a database
schema using the jOOQ library. These generated classes provide type-safe access
to your database tables, views, and stored procedures, allowing for a more
robust and maintainable codebase.

### Features

- Generates Java classes for database tables, views, and stored procedures.
- Type-safe SQL queries.
- Customizable code generation settings.

### Compile and Run JooqCodegen

```bash
cd udi-prime/support/jooq
java -cp "../lib/*:./lib/*" JooqCodegen.java $JDBC_URL <SCHEMA> <PACKAGE_NAME> <DIRECTORY> <JAR_NAME>
```

Replace `<SCHEMA>`, `<PACKAGE_NAME>`, `<DIRECTORY>`, and `<JAR_NAME>` with
your specific values.

To see how to use it:

```bash
cd udi-prime
./udictl.ts generate java jooq --help
```

### Configuration

- **JooqCodegen.java**: The main Java class responsible for configuring and
  executing the jOOQ code generation process.

### How It Works

1. **Configuration**: The `JooqCodegen` class sets up the jOOQ `Configuration`
   object with the database connection URL, schema, and target package and
   directory for the generated classes.
2. **Code Generation**: jOOQ's `GenerationTool` is used to generate the classes
   based on the provided configuration.
3. **Compilation and Packaging**: The generated Java classes are compiled and
   packaged into a JAR file.

## Hibernate Code Generation (TODO)

### Overview

This section will cover setting up Hibernate code generation, including
generating entity classes and JPA mappings based on the database schema.

### Features

- Generates JPA entity classes from database schema.
- Customizable code generation settings.
- Integration with Maven for easy execution.

### Prerequisites

- Java 21 or higher
- Maven
- PostgreSQL database (or another supported database with appropriate driver)

### Setup

1. **Clone the Repository**
   ```sh
   git clone https://github.com/your-repo/codegen-utils.git
   cd codegen-utils
   ```

2. **Configure Environment Variables** Set the `JDBC_URL` environment variable
   to your database connection URL.
   ```sh
   export JDBC_URL=jdbc:postgresql://localhost:5432/yourdatabase
   ```

3. **Run Code Generation** Execute the Maven command to generate the Hibernate
   entity classes.
   ```sh
   mvn clean compile hibernate3:hbm2java
   ```

### Configuration

- **pom.xml**: This file will include the necessary dependencies for Hibernate
  and configures the Hibernate Tools Maven plugin to generate the entity
  classes.
- **hibernate.cfg.xml**: The Hibernate configuration file that sets up the
  database connection and other Hibernate settings.

### How It Works

1. **Configuration**: The Hibernate configuration file (`hibernate.cfg.xml`)
   sets up the database connection URL, schema, and other settings.
2. **Code Generation**: The Hibernate Tools Maven plugin
   (`hibernate3-maven-plugin`) is used to generate the entity classes based on
   the provided configuration.

### TODO

- Implement Hibernate code generation configuration in `pom.xml`.
- Add a detailed step-by-step guide for Hibernate code generation setup and
  execution.

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file
for details.
