# TechBD Polyglot Monorepo

Welcome to the **TechBD Polyglot Prime** repository! This repository is the
central hub for all bespoke code managed by **Technology By Design (TechBD)**.
Our goal is to maintain a well-organized, scalable, and efficient monorepo that
supports our diverse range of projects and technologies.

## Primary Languages and Frameworks

At TechBD, we focus on the following primary languages and frameworks for our
enterprise and utility projects:

- **Modern Java 21 LTS and above with Spring Boot 3 and above** for all API and
  HTTP service-related use cases.
- **Deno and TypeScript** for utilities and scripting where Java may be too
  heavy.

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
polyglot-prime/
│
├── fhir/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── org/
│   │   │   │       └── techbd/
│   │   │   │           └── service/
│   │   │   │               └── api/
│   │   │   │                   └── http/
│   │   │   │                       └── fhir/
│   │   │   └── resources/
│   │   └── test/
│   ├── pom.xml
│   └── README.md
│
├── lib/
│   └── README.md
│
└── support/
    └── README.md
```

### Project: FHIR

The `fhir` project is a Java Spring Boot application which serves FHIR API
endpoints.

#### Directory Structure

- **src/main/java/org/techbd/service/**: Contains the Java source code for the
  FHIR services.
- **src/main/resources/**: Contains configuration files and static resources.
- **src/test/java/org/techbd//**: Contains unit and integration tests.
- **pom.xml**: Maven configuration file for building the project.
- **README.md**: Documentation specific to the 1115 Waiver project.

#### Project Setup

To set up the 1115 Waiver project, follow these steps:

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/tech-by-design/polyglot-prime.git
   cd polyglot-prime/fhir
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
