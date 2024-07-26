# Universal Data Infrastructure (UDI)

## Overview

UDI leverages Deno to generate SQL and PlantUML diagrams for a UDI model. It
utilizes [SQL Aide](https://www.sql-aide.com/) to type-safely define database
schema definitions, which can then be generated as PostgreSQL scripts or
visualized as diagrams.

Following `SQLa`'s philosophy, all SQL should be generated into files that can
be inspected and version-controlled and be executed using `psql` as a separate
step.

Additionally, for the UDI Prime SQL generation and usage, we utilize ISLM migration, a tool available in SQL Aide. This tool helps manage schema migrations in PostgreSQL. The process also integrates session orchestration tables from the CSV ingestion orchestration process within the techbd_orch_ctl schema.

### Features

- **SQL Generation**: Automatically generate SQL scripts for creating UDI
  structures in a PostgreSQL database.
- **Diagram Generation**: Produce PlantUML diagrams to visualize the relational
  structure of the UDI model.
- **Configurable**: Supports customization options like schema destruction and
  naming through command-line arguments.
- **ISLM Migration**: Facilitates schema migrations in PostgreSQL, ensuring a smooth upgrade and downgrade process for database schemas.  

## Prerequisites

- **Deno**: Ensure [Deno](https://deno.land/) is installed on your machine.
- **PostgreSQL**: A PostgreSQL server must be accessible for deploying the SQL
  scripts.
- **.pgpass Configuration**: For deploying scripts to PostgreSQL, configure your
  `.pgpass` file and use
  [pgpass](https://github.com/netspective-labs/sql-aide/tree/main/lib/postgres/pgpass)
  for password-less authentication.
- **ISLM Migration**: Ensure the ISLM migration schemas and procedures are installed, which are part of the [SQL Aide package](https://github.com/netspective-labs/sql-aide/tree/main/lib/postgres/islm), for handling database migrations.  
- **techbd_orch_ctl Schema**: Ensure we have the `techbd_orch_ctl` schema and the 10 tables `business_rules`, `demographic_data`, `device`, `orch_session`, `orch_session_entry`, `orch_session_exec`, `orch_session_issue`, `orch_session_state`, `qe_admin_data`, `screening` available as a result of CSV ingestion process. 

## Installation

Clone the repository:

```bash
git clone https://github.com/tech-by-design/polyglot-prime.git
cd polyglot-prime
```

## Usage

The tool provides three main functionalities: generating SQL scripts, generating
PlantUML diagrams, and executing SQL scripts against a PostgreSQL database.

### Generating SQL, Java, HTML and ERD

To generate SQL and ERD for the UDI model, run the following command:

```bash
cd udi-prime

# "freshen" a development database and generate everything all at once
./udictl.ts ic omnibus-fresh

# when you want to run specific commands individually

./udictl.ts ic generate --help  # review code generation options
./udictl.ts ic generate sql     # generate all *.sql artifacts

# generate all *.java artifacts using jOOQ library and store in a JAR file
# and then `cat` the details of what was generated (the MANIFEST.MF);
# IMPORTANT: `generate java jooq` uses JDBC meta data retrieved from the DB
#            so be sure to only run `generate java` after migration succeeds.
./udictl.ts ic generate java jooq 
# review the META-INF/MANIFEST.MF `Generated-*` content for accuracy
# run `mvn clean` to clear your cache of the older JAR file.

./udictl.ts ic generate docs               # generate all documentation (e.g. SchemaSpye) artifacts
./udictl.ts ic generate docs --serve 4343  # generate and serve the documents at localhost:4343
```

`./udictl.ts ic generate sql` command will produce SQL files in
`./target/postgres/ingestion-center` that includes all necessary DDL
statements for setting up the database schema.

`./udictl.ts ic generate docs` command will produce HTML files in
`./target/docs/schema-spy` that includes HTML, diagrams, and documentation to
explain the database schema.

### Loading SQL to PostgreSQL for Migration

In addition to generating the SQL script, you can load it to your PostgreSQL 
database for migration using one of the following `udictl.ts ic load-sql` commands:

```bash
cd udi-prime
./udictl.ts ic load-sql --help           # review SQL migration options
./udictl.ts ic load-sql --destroy-first  # use generated SQL to first destroy all SQL objects in and then perform migrations
./udictl.ts ic load-sql                  # use generated SQL to perform migrations without destroying existing objects

./udictl.ts ic test --help              # review test options
./udictl.ts ic test                     # perform pgTAP tests
```

`./udictl.ts ic load-sql` executes the SQL scripts using credentials stored in
your `.pgpass` file and uses
[pgpass](https://github.com/netspective-labs/sql-aide/tree/main/lib/postgres/pgpass)
for password-less authentication.

Add the following `UDI_PRIME_DESTROYABLE_DEVL` connection ID to your
`~/.pgpass`:

```
# { id: "UDI_PRIME_DESTROYABLE_DEVL", description: "UDI Prime database that can be destroyed", boundary: "Development" } 
DB_HOST:5432:DB_NAME:USER_NAME:PASSWORD
```

The `udictl.ts ic load-sql` and `udictl.ts ic test` commands use `psql`
PostgreSQL client. If you do not have installed, you can use any package manager
to install it:

```bash
$ sudo upt install -y postgresql-client
```

### Executing Migration in PostgreSQL

Once generated SQL is loaded in the database, you can execute the migration in your PostgreSQL
database using one of the following `udictl.ts ic migrate` commands:

```bash
cd udi-prime
./udictl.ts ic migrate --help           # review SQL migration options
./udictl.ts ic migrate                  # use generated SQL to perform migrations without destroying existing objects

```

## UDI Naming Standards

- `src/*` directory should only have src (not generated) files
- `target/*` directory should contain generated files
- `*.sql` extension should be used for ANSI SQL (not PostgreSQL-specific, for
  example)
- `*.psql` extension should be used for PostgreSQL-specific files (using stored
  routines, for example)
- `*.auto.*` are auto-generated files (always include `.auto.` for generated
  files)

## UDI `search_path` Standards

Don't use `search_path` in PostgreSQL Scripts.

### Pros of Using `search_path`

1. **Schema Flexibility**: `search_path` allows you to easily switch between
   different schemas without changing the schema names in your queries. This can
   be useful in multi-tenant applications where each tenant's data resides in a
   separate schema.
2. **Simplifies SQL Statements**: By setting the `search_path`, you can omit the
   schema name in your SQL statements, making them cleaner and easier to read.
3. **Environment Configuration**: Different environments (e.g., development,
   staging, production) can have different schemas, and `search_path` allows the
   same script to run in all environments without modification.

### Cons of Using `search_path`

1. **Ambiguity and Errors**: If multiple schemas contain objects with the same
   name, relying on `search_path` can lead to ambiguity and unexpected behavior,
   as PostgreSQL will use the first match it finds based on the `search_path`
   order.
2. **Security Risks**: Misconfigured `search_path` can lead to security issues,
   such as executing unintended functions or accessing unintended tables.
3. **Maintenance Challenges**: Relying heavily on `search_path` can make scripts
   harder to maintain and debug, as it’s not immediately clear which schema an
   object belongs to.
4. **Performance Impact**: PostgreSQL needs to search through the schemas listed
   in `search_path` to resolve object names, which can introduce slight
   performance overhead.

### UDI Practices

1. **Explicit Schema Qualification**: Where possible, especially in SQL DDL,
   explicitly qualify object names with their schema. This removes ambiguity and
   ensures that the correct objects are being accessed.
   ```sql
   SELECT * FROM my_schema.my_table;
   ```

2. **Minimal Use of `search_path`**: Use `search_path` sparingly and primarily
   in contexts where its benefits outweigh the drawbacks. For example, use it in
   application-specific settings or for temporary session-specific tasks.
   ```sql
   SET search_path TO my_schema;
   ```

3. **Set `search_path` per Session**: If you need to use `search_path`, set it
   at the beginning of a session or transaction to ensure consistency
   throughout.
   ```sql
   BEGIN;
   SET search_path TO my_schema;
   -- Your SQL operations
   COMMIT;
   ```

4. **Document Schema Assumptions**: Clearly document any assumptions or
   dependencies on `search_path` within your scripts to aid in maintenance and
   troubleshooting.

5. **Role-Specific Paths**: Consider setting role-specific `search_path`
   settings if different roles are intended to operate in different schemas.
   ```sql
   ALTER ROLE my_role SET search_path TO my_schema;
   ```

6. **Schema Order**: Be cautious about the order of schemas in the
   `search_path`. The first schema in the list is searched first, so place the
   most critical schemas first.
   ```sql
   SET search_path TO critical_schema, common_schema, public;
   ```

By following these practices, you can leverage the benefits of `search_path`
while mitigating its potential downsides. The key is to strike a balance between
flexibility and clarity, ensuring that your database interactions remain
predictable and secure.
