# Tech by Design Polyglot Monorepo

Welcome to the **Tech by Design Polyglot Prime** repository! This repository is the
central hub for all bespoke code managed by **Technology By Design (Tech by Design)**.
Our goal is to maintain a well-organized, scalable, and efficient monorepo that
supports our diverse range of projects and technologies.

## Primary Languages and Frameworks

At Tech by Design, we focus on the following primary languages and frameworks for our
enterprise and utility projects:

- Modern Java 21 LTS and above with Spring Boot 3.3 and above for all API and
  HTTP service-related use cases.
- Maven for project management.
- Thymeleaf for HTML templating and HTMX 2.0 for HATEOS interactions.
- OpenTelemetry (observability) and OpenFeature (feature flags).
- jOOQ with automatic code generation for type-safe SQL-first database
  interactions. 
- PostgreSQL 16 for server-side persistence and SQLite for edge-side
  persistence. 
- JUnit 5 with AssertJ assertions for testing the app server, Playwright for
  testing the front end, and pgTAP for testing the database.
- Deployment via containers
- Deno and TypeScript for utilities and scripting where Java may be too heavy.

## Monorepo Strategy

Inspired by the practices at Microsoft, Google, and other large software
companies, we have designed our monorepo strategy to facilitate collaboration,
maintainability, and scalability. Here are the key aspects of our strategy:

1. **Modular Structure**: Each top-level directory represents a distinct project
   or service. This allows for clear separation of concerns and easy navigation.
2. **Consistent Naming Conventions**: Follow consistent naming conventions to
   make it easier to locate and manage code.
3. **Shared Libraries**: Common libraries and utilities will be placed in a
   shared directory to promote code reuse.
4. **Version Control**: Use Git submodules or subtree for managing third-party
   dependencies to keep the repository clean and manageable.
5. **CI/CD Integration**: Integrate Continuous Integration and Continuous
   Deployment (CI/CD) pipelines for automated testing and deployment.
6. **Documentation**: Each project will contain comprehensive documentation to
   assist developers in understanding and contributing to the codebase.

## Repository Structure

```
.
├── hub-prime
│   ├── lib
│   ├── src
│   │   ├── main
│   │   │   ├── java
│   │   │   │   └── org
│   │   │   │       └── Tech by Design
│   │   │   │           ├── conf
│   │   │   │           ├── orchestrate
│   │   │   │           │   ├── fhir
│   │   │   │           │   └── sftp
│   │   │   │           ├── service
│   │   │   │           │   └── http
│   │   │   │           │       ├── filter
│   │   │   │           │       └── hub
│   │   │   │           ├── udi
│   │   │   │           └── util
│   │   │   └── resources
│   │   │       ├── META-INF
│   │   │       ├── public
│   │   │       └── templates
│   │   │           ├── fragments
│   │   │           ├── layout
│   │   │           ├── login
│   │   │           ├── mock
│   │   │           │   └── shinny-data-lake
│   │   │           │       └── 1115-validate
│   │   │           └── page
│   │   │               └── interactions
│   │   ├── site
│   │   │   └── markdown
│   │   └── test
│   │       └── java
│   │           └── org
│   │               └── Tech by Design
│   │                   ├── orchestrate
│   │                   │   └── fhir
│   │                   ├── service
│   │                   │   └── http
│   │                   │       └── hub
│   │                   └── util
│   └── target
|
└── udi-prime
    ├── lib
    ├── src
    │   ├── main
    │   │   └── postgres
    │   │       └── ingestion-center
    │   └── test
    │       └── postgres
    │           └── ingestion-center
    ├── support
    │   └── jooq
    │       └── lib
    └── target
```

### Project: Tech by Design Primary Hub

The `hub-prime` project is a Java Spring Boot application which serves FHIR API
endpoints.

#### Project Setup

To set up the `Tech by Design Hub` project, follow these steps:

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/tech-by-design/polyglot-prime.git
   cd polyglot-prime
   direnv allow               # apply the env vars
   cp .envrc.example .envrc   # assume the use of direnv
   vi .envrc                  # make sure to store secrets in ENV or Vault, not in Git
   cd hub-prime
   ```

2. **Build the Project**:
   ```bash
   mvn clean install
   ```

3. **Run the Application**:
   ```bash
   mvn spring-boot:run
   ```

4. **Access the Application**: Open your browser and navigate to
   `http://localhost:8080`.

### Shared Libraries

- `lib`: Contains reusable utility functions and classes that can be used across
  different projects.

### Supporting Work Products

- `support` contains all work products that _support_ the above but do not make
  their way into production


<!-- Security scan triggered at 2025-09-01 23:56:56 -->

<!-- Security scan triggered at 2025-09-02 00:52:44 -->

<!-- Security scan triggered at 2025-09-02 02:22:57 -->