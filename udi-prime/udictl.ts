#!/usr/bin/env -S deno run --allow-all
import * as path from "https://deno.land/std@0.224.0/path/mod.ts";
import $ from "https://deno.land/x/dax@0.39.2/mod.ts";
import {
  Command,
  EnumType,
} from "https://deno.land/x/cliffy@v1.0.0-rc.4/command/mod.ts";
import * as ic from "./src/main/postgres/ingestion-center/mod.ts";
import {
  ConsoleHandler,
  FileHandler,
  getLogger,
  setup,
} from "https://deno.land/std@0.224.0/log/mod.ts";
import { serveDir } from "https://deno.land/std@0.224.0/http/file_server.ts";

const setupLogger = (options: { readonly logResults?: string }) => {
  if (options.logResults) {
    setup({
      handlers: {
        file: new FileHandler("DEBUG", { filename: options.logResults }),
      },
      loggers: { default: { level: "DEBUG", handlers: ["file"] } },
    });
  } else {
    setup({
      handlers: { console: new ConsoleHandler("DEBUG", {}) },
      loggers: { default: { level: "DEBUG", handlers: ["console"] } },
    });
  }
  return getLogger();
};

const postreSqlClientMinMessagesLevels = [
  "panic",
  "fatal",
  "error",
  "warning",
  "notice",
  "log",
  "debug1",
  "debug2",
  "debug3",
  "debug4",
  "debug5",
] as const;
const postreSqlClientMinMessagesLevelCliffyEnum = new EnumType(
  postreSqlClientMinMessagesLevels,
);

type PostreSqlClientMinMessagesLevel =
  typeof postreSqlClientMinMessagesLevels[number];

const postgreSqlClientMinMessagesSql = (
  level: PostreSqlClientMinMessagesLevel,
) => `SET client_min_messages TO ${level};`;

const toLocalPath = (input: string | URL) =>
  (input instanceof URL ? input : new URL(input)).protocol === "file:"
    ? Deno.realPathSync(new URL(input).pathname)
    : input;

// TODO: add a `doctor` command to check for dependencies like `psql`, PostgreSQL JDBC driver, SchemaSpy JAR, Schema Crawler JAR, etc.
// TODO: in `doctor`, if you get this error: `java.lang.RuntimeException: Fontconfig head is null, check your fonts or fonts configuration` this is required:
//       sudo apt update && sudo apt install fontconfig fonts-dejavu

// deno-fmt-ignore
await new Command()
  .name("UDI Control Plane")
  .version("0.1.0")
  .description("Universal Data Infrastructure (UDI) Orchestration")
  .command("ic", new Command()
    .description("UDI Ingestion Center (IC) subject area commands handler")
    .command("generate", new Command()
      .description("Generate SQL and related artifacts")
      .command("sql", "Generate SQL artifacts")
        .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: "./target/main/postgres/ingestion-center" })
        .option("--destroy-fname <file-name:string>", "Filename of the generated destroy script in target", { default: "destroy.auto.psql" })
        .option("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver.auto.psql" })
        .option("--overwrite", "Don't remove existing target directory first, overwrite instead")
        .option("--docs-schemaspy <path:string>", "Generate SchemaSpy documentation", { default: "./target/docs/schema-spy" })
        .option("--log-results <path:string>", "Store generator results in this log file")
        .action((options) => {
          const logger = setupLogger(options);
          if(!options.overwrite) {
              try {
                  Deno.removeSync(options.target, { recursive: true });
              } catch (_notFound) {
                  // directory doesn't exist, it's OK
              }
          }
          Deno.mkdirSync(options.target, { recursive: true });
          const generated = ic.generated();
          Deno.writeTextFileSync(`${options.target}/${options.driverFname}`, generated.driverSQL);
          Deno.writeTextFileSync(`${options.target}/${options.destroyFname}`, generated.destroySQL);
          logger.debug(`${options.target}/${options.driverFname}`);
          logger.debug(`${options.target}/${options.destroyFname}`);
          [...generated.dependencies, ...generated.testDependencies].forEach((dep) => {
            const depLocal = toLocalPath(dep);
            Deno.copyFileSync(depLocal, `${options.target}/${path.basename(dep)}`);
            logger.debug(depLocal);
          });
        })
      .command("java", new Command()
        .description("Generate Java code artifacts")
        .command("jooq", "Generate jOOQ code packages")
          .option("--build-dir <path:string>", "Destination for package", { default: "./target/java/auto/jooq/ingress" })
          .option("--package <name:string>", "Java package name", { default: "org.techbd.udi.auto.jooq.ingress" })
          .option("--schema <name:string>", "Schema to inspect", { default: "techbd_udi_ingress" })
          .option("--jar <path:string>", "The JAR file to create", { default: "../hub-prime/lib/techbd-udi-auto-jooq-ingress.jar" })
          .option("--pgpass <path:string>", "`pgpass` command", { required: true, default: "pgpass" })
          .option("-c, --conn-id", "pgpass connection ID to use for JDBC URL", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
          .action(async (options) => {
            try {
                Deno.removeSync(options.buildDir, { recursive: true });
                Deno.removeSync(options.jar);
            } catch (_notFound) {
                // directory doesn't exist, it's OK
            }
            const jdbcURL = await $`${options.pgpass} prepare '\`jdbc:postgresql://\${conn.host}:\${String(conn.port)}/\${conn.database}?user=\${conn.username}&password=\${conn.password}\`' --conn-id=${options.connId}`.text();
            await $`java -cp "./lib/*:./support/jooq/lib/*" support/jooq/JooqCodegen.java ${jdbcURL} ${options.schema} ${options.package} ${options.buildDir} ${options.jar}`;
            console.log("Java jOOQ generation complete, JAR file", options.jar);
          })
       )
      .command("docs", "Generate documentation artifacts")
        .option("--schemaspy-dest <path:string>", "Generate SchemaSpy documentation", { default: "./target/docs/schema-spy" })
        .option("--pgpass <path:string>", "`pgpass` command", { required: true, default: "pgpass" })
        .option("-c, --conn-id", "pgpass connection ID to use for SchemaSpy database credentials", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
        .option("--serve <port:number>", "Serve generated documentation at port")
        .action(async (options) => {
          const schemaSpyCreds = await $`${options.pgpass} prepare '\`-host \${conn.host} -port \${String(conn.port)} -db \${conn.database} -u \${conn.username} -p \${conn.password}\`' --conn-id=${options.connId}`.text();
          await $.raw`java -jar ./lib/schemaspy-6.2.4.jar -t pgsql11 -dp ./lib/postgresql-42.7.3.jar -schemas techbd_udi_ingress ${schemaSpyCreds} -debug -o ${options.schemaspyDest} -vizjs`;
          if(options.serve) {
              Deno.serve({ port: options.serve }, (req) => {
                return serveDir(req, {
                  fsRoot: options.schemaspyDest,
                });
              });
            }
        })
    )
    .command("migrate", "Use psql to execute generated migration scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: "./target/main/postgres/ingestion-center" })
      .option("--destroy-fname <file-name:string>", "Filename of the generated destroy script in target", { default: "destroy.auto.psql" })
      .option("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver.auto.psql" })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--pgpass <path:string>", "`pgpass` command", { required: true, default: "pgpass" })
      .option("--destroy-first", "Destroy objects before migration")
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./udictl-migrate-${new Date().toISOString()}.log` })
      .option("-c, --conn-id", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .type("pg-client-min-messages-level", postreSqlClientMinMessagesLevelCliffyEnum)
      .option("-l, --psql-log-level <level:pg-client-min-messages-level>", "psql `client_min_messages` level.", {
        default: "warning",
      })
      .action(async (options) => {
        const logger = setupLogger(options);
        const psqlCreds = await $`${options.pgpass} psql-fmt --conn-id=${options.connId}`.text();
        if(options.destroyFirst) {
            const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${options.target}/${options.destroyFname}`.captureCombined();
            logger.debug(`-- DESTROYING FIRST WITH ${options.destroyFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`);
            logger.debug(psqlResults.combined);
            logger.debug(`-- END ${options.destroyFname} at ${new Date()}\n\n`);
        }
        const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${options.target}/${options.driverFname}`.captureCombined();
        logger.debug(`-- BEGIN ${options.driverFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`);
        logger.debug(psqlResults.combined);
        logger.debug(`-- END ${options.driverFname} at ${new Date()}\n\n`);
        console.log("Migration complete, results logged in", options.logResults);
      })
    .command("test", "Use psql to execute pgTAP scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: "./target/main/postgres/ingestion-center" })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--pgpass <path:string>", "`pgpass` command", { required: true, default: "pgpass" })
      .option("--suite-fname <file-name:string>", "Filename of the generated test suite script in target", { default: "suite.pgtap.psql" })
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./udictl-test-${new Date().toISOString()}.log` })
      .option("-c, --conn-id", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .type("pg-client-min-messages-level", postreSqlClientMinMessagesLevelCliffyEnum)
      .option("-l, --psql-log-level <level:pg-client-min-messages-level>", "psql `client_min_messages` level.", {
        default: "warning",
      })
      .action(async (options) => {
        const logger = setupLogger(options);
        const psqlCreds = await $`${options.pgpass} psql-fmt --conn-id=${options.connId}`.text();
        const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${options.target}/${options.suiteFname}`.captureCombined();
        logger.debug(`-- BEGIN ${options.suiteFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`);
        logger.debug(psqlResults.combined);
        logger.debug(`-- END ${options.suiteFname} at ${new Date()}\n\n`);
        console.log("Test complete, results logged in", options.logResults);
      })
    )
  .parse(Deno.args);
