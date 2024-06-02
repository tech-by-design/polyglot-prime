# Universal Data Infrastructure (UDI) Model

## Overview

This tool leverages Deno to generate SQL and PlantUML diagrams for a UDI model.
It utilizes custom TypeScript modules from the
[SQL Aide](https://www.sql-aide.com/) library to dynamically create database
schema definitions, which can then be exported to SQL scripts or visualized as
diagrams.

### Features

- **SQL Generation**: Automatically generate SQL scripts for creating UDI
  structures in a PostgreSQL database.
- **Diagram Generation**: Produce PlantUML diagrams to visualize the relational
  structure of the UDI model.
- **Configurable**: Supports customization options like schema destruction and
  naming through command-line arguments.

## Prerequisites

- **Deno**: Ensure [Deno](https://deno.land/) is installed on your machine.
- **PostgreSQL**: A PostgreSQL server must be accessible for deploying the SQL
  scripts.
- **.pgpass Configuration**: For deploying scripts to PostgreSQL, configure your
  `.pgpass` file for password-less authentication.

## Installation

Clone the repository and navigate to the directory containing the UDI tool:

```bash
git clone https://github.com/tech-by-design/polyglot-prime.git
```

## Usage

The tool provides three main functionalities: generating SQL scripts, generating
PlantUML diagrams, and executing SQL scripts against a PostgreSQL database.

### Generating SQL Script

To generate SQL for the UDI model, run the following command:

```bash
deno run -A udi-prime/src/postgres/udi-ingestion-center.ts sql > udi-prime/src/postgres/udi-ingestion-center.auto.psql
```

This command will produce a psql file that includes all necessary DDL statements
for setting up the database schema.

### Generating PlantUML Diagram

To generate a PlantUML diagram of the UDI model, use the following command:

```bash
deno run -A udi-prime/src/postgres/udi-ingestion-center.ts diagram > udi-prime/src/postgres/udi-ingestion-center.auto.puml
```

This will create a `.puml` file which can be viewed or edited in any UML diagram
tool that supports PlantUML.

### Deploying SQL to PostgreSQL

After generating the SQL script, you can deploy it to your PostgreSQL database
using:

```bash
fish -c "psql -f udi-prime/src/postgres/udi-ingestion-center.auto.psql $(pgpass psql-fmt --conn-id='UDI_NEON_DEVL')"
```

This command utilizes Fish shell to execute the SQL script using credentials
stored in your `.pgpass` file.

## Configuration

You can configure the behavior of the SQL and diagram generation through the
script's command-line interface:

- **--destroyFirst**: If specified, the tool will drop the existing schema
  before creating a new one.
- **--schemaName**: Allows specifying a custom schema name for the generated SQL
  and diagrams.
