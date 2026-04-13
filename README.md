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
├── api-automation/                   # TypeScript/Playwright API test automation and validation
│   ├── sections/                     # Request and validation sections
│   │   └── request_validate_data.ts
│   ├── testdata/                     # Test data and fixtures
│   │   ├── expectedValidationIssues.ts
│   │   └── FHIR-Data/
│   ├── tests/                        # Test cases
│   │   ├── FHIR-BundleNegative.test.ts
│   │   └── FHIR-BundlePositive.test.ts
│   └── utils/                        # Testing utilities
│       └── logger-util.ts
│
├── hub-core-lib/                         # Shared Java core library for common utilities and components
│   ├── src/
│   │   ├── main/
│   │   │   └── java/
│   │   └── test/
│   │       └── java/
│   └── lib/                          # External dependencies
│
├── csv-service/                      # Java service for CSV file processing and transformation
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── org/techbd/
│   │   │   └── resources/
│   │   └── test/
│   │       └── java/
│   └── lib/                          # External dependencies
│
├── fhir-validation-service/          # Java service for FHIR compliance validation
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── org/techbd/
│   │   │   └── resources/
│   │   └── test/
│   │       └── java/
│   └── lib/                          # External dependencies
│
├── hub-prime/                        # Primary Spring Boot FHIR API hub and UI application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── org/techbd/
│   │   │   │       ├── conf/        # Configuration classes
│   │   │   │       ├── controller/  # REST controllers
│   │   │   │       ├── orchestrate/ # FHIR and SFTP orchestration
│   │   │   │       │   ├── fhir/
│   │   │   │       │   └── sftp/
│   │   │   │       ├── service/     # Business logic services
│   │   │   │       │   ├── http/
│   │   │   │       │   │   ├── filter/     # Security and request filters
│   │   │   │       │   │   ├── hub/        # Hub-specific logic
│   │   │   │       │   │   └── *.java      # Config, constants, security
│   │   │   │       │   └── *.java
│   │   │   │       └── util/        # Utility helpers
│   │   │   └── resources/
│   │   │       ├── META-INF/
│   │   │       ├── public/          # Static web assets
│   │   │       └── templates/       # Thymeleaf templates
│   │   │           ├── fragments/
│   │   │           ├── layout/
│   │   │           ├── login/
│   │   │           ├── mock/        # Mock data templates
│   │   │           └── page/        # Page templates
│   │   ├── site/
│   │   │   └── markdown/
│   │   └── test/
│   │       └── java/
│   │           └── org/techbd/
│   │               ├── orchestrate/ # Tests for orchestration
│   │               ├── service/     # Tests for services
│   │               └── util/        # Tests for utilities
│   └── lib/                         # External dependencies
│
├── integration-artifacts/            # Integration configurations, scripts, and templates for various data formats
│   ├── aws-queue-listener/          # AWS SQS integration
│   ├── ccda/                        # CCDA format templates
│   ├── custom-lib/                  # Custom libraries
│   ├── fhir/                        # FHIR-specific artifacts
│   ├── flatfile/                    # Flat file processing templates
│   ├── global-scripts/              # Shared integration scripts
│   ├── hl7v2/                       # HL7v2 format templates
│   └── lookup-manager/              # Lookup table management
│
├── nexus-core-lib/                   # Nexus-specific core library shared across nexus services
│   ├── src/
│   │   ├── main/
│   │   │   └── java/
│   │   └── test/
│   │       └── java/
│   └── lib/
│
├── nexus-ingestion-api/              # Nexus ingestion API service
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── org/techbd/
│   │   │   └── resources/
│   │   └── test/
│   │       └── java/
│   └── lib/
│
├── support/                          # Supporting tools, documentation, and non-production work products
│   ├── bin/                         # Utility scripts and binaries
│   ├── containers/                  # Docker and container configs
│   ├── nyec-ig-version/             # NYEC IG versioning tools
│   ├── qualityfolio/                # Quality metrics and dashboards
│   ├── release-notes/               # Release documentation
│   ├── service/                     # Service-level tools
│   ├── specifications/              # Technical specifications
│   └── testcases/                   # Manual test cases
│
├── test-automation/                  # Test automation scripts for smoke testing and QA
│   ├── FHIR-Bundle-SmokeTest-Devl/
│   ├── FHIR-Bundle-SmokeTest-PHI-QA/
│   ├── FHIR-Bundle-SmokeTest-Stage/
│   ├── CCDA-Bundle-SmokeTest-PHI-QA/
│   ├── CCDA-Bundle-SmokeTest-Stage/
│   ├── CSV-Bundle-SmokeTest-PHI-QA/
│   ├── CSV-Bundle-SmokeTest-Stage/
│   ├── HL7-Bundle-SmokeTest-PHI-QA/
│   └── HL7-Bundle-SmokeTest-Stage/
│
└── udi-prime/                        # UDI ingestion center with PostgreSQL database and jOOQ code generation
    ├── src/
    │   ├── main/
    │   │   └── postgres/            # PostgreSQL DDL scripts
    │   │       └── ingestion-center/
    │   └── test/
    │       └── postgres/
    │           └── ingestion-center/
    ├── support/
    │   └── jooq/                    # jOOQ code generation
    │       └── lib/
    └── lib/                         # External dependencies
```

### Core Projects

#### hub-prime
Primary Spring Boot 3.3+ FHIR API hub application with Thymeleaf UI and HTMX interactions. Handles FHIR bundle ingestion, validation, and API endpoints.

#### udi-prime
UDI (Unified Data Intake) ingestion center with PostgreSQL backend. Uses jOOQ for type-safe SQL operations and includes database migrations and code generation.

#### nexus-ingestion-api
Nexus-specific ingestion API service for data integration and processing.

### Supporting Services

#### csv-service
Java-based service for CSV file processing, validation, and transformation to standard formats.

#### fhir-validation-service
Dedicated FHIR compliance validation service that validates data against FHIR specifications.

### Shared Libraries

#### hub-core-lib
Shared Java library containing common utilities, models, and components used across multiple services.

#### nexus-core-lib
Nexus-specific core library with shared functionality for nexus-related services.

### Testing & Automation

#### api-automation
TypeScript/Playwright-based API automation framework for testing REST endpoints and validating responses.

#### test-automation
Smoke test automation suites for different environments (Development, QA, Stage, Production) across FHIR, HL7, CCDA, and CSV bundles.

### Integration & Scripts

#### integration-artifacts
Contains integration configurations, global scripts, and templates for various data formats (FHIR, HL7v2, CCDA, Flatfile, etc.)

#### support
Supporting work products including documentation, specifications, release notes, testing tools, and non-production utilities.

## Project Setup

To set up the **Tech by Design Polyglot Prime** monorepo, follow these steps:

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/tech-by-design/polyglot-prime.git
   cd polyglot-prime
   direnv allow               # apply the env vars
   cp .envrc.example .envrc   # assume the use of direnv
   vi .envrc                  # make sure to store secrets in ENV or Vault, not in Git
   ```

2. **Build `hub-prime` with all dependenvy modules**:
   ```bash
   mvn clean install
   ```

3. **Run the Primary Hub Application**:
   ```bash
   cd hub-prime
   mvn spring-boot:run
   ```

4. **Access the Application**: Open your browser and navigate to
   `http://localhost:8080`.

## Development Workflow

Each top-level directory is a separate Maven module or project. Here's the recommended workflow:

1. Make changes to your specific project
2. Run tests: `mvn test` in the project directory
3. Build the project: `mvn clean install`
4. For integration changes, rebuild the entire monorepo: `mvn clean install` from root

