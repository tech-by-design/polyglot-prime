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

## Refactor `fhir` to `hub-prime`

Migrating from `fhir` monorepo project (specific) to `hub-prime` (universal).

- Find all references to `fhir` directory and change to `hub-prime`.
- moved everything from `application.properties` to `application.yml`
  - expect all env vars to be `${SPRING_PROFILE_ACTIVE}_TECHBD_*`
- `export SPRING_PROFILES_ACTIVE=sandbox` is now required for sandbox / prod
- add `www.hub.techbd.org` as primary web site with `www.hub.devl.techbd.org`
  and `www.hub.stage.techbd.org` environments.
  - `synthetic.fhir.api.techbd.org` will be for FHIR APIs only
  - `synthetic.api.techbd.org` will be for APIs only
- removed jOOQ dependency for now

### TODO:

- test different profiles in Containers (for promotion to ECS)
- `.github/workflows/deploy-techbd-org.yml` change `fhir` to `hub-prime`
- get FHIR engines from user agent

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
└── hub-prime
    ├── src
    │   ├── main
    │   │   ├── java
    │   │   │   └── org
    │   │   │       └── techbd
    │   │   │           ├── conf
    │   │   │           ├── orchestrate
    │   │   │           │   └── fhir
    │   │   │           ├── service
    │   │   │           │   └── http
    │   │   │           │       └── hub
    │   │   │           │           └── prime
    │   │   │           ├── sql
    │   │   │           ├── udi
    │   │   │           │   └── entity
    │   │   │           └── util
    │   │   └── resources
    │   │       ├── META-INF
    │   │       ├── sql
    │   │       │   └── artifact
    │   │       └── templates
    │   │           └── mock
    │   │               └── shinny-data-lake
    │   │                   └── 1115-validate
    │   └── test
    │       └── java
    │           └── org
    │               └── techbd
    │                   ├── orchestrate
    │                   │   └── fhir
    │                   ├── service
    │                   │   └── http
    │                   │       └── hub
    │                   │           └── prime
    │                   └── util
    └── target
        ├── site
        └── surefire-reports
```

### Project: TechBD Primary Hub

The `hub-prime` project is a Java Spring Boot application which serves FHIR API
endpoints.

#### Project Setup

To set up the `TechBD Hub` project, follow these steps:

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/tech-by-design/polyglot-prime.git
   cd polyglot-prime
   cp .envrc-example .envrc   # assume the use of direnv
   vi .envrc                  # make sure to store secrets in ENV or Vault, not in Git
   direnv allow               # apply the env vars
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
