#!/usr/bin/env -S deno run --allow-all
import { serveDir } from "https://deno.land/std@0.224.0/http/file_server.ts";
import {
  ConsoleHandler,
  FileHandler,
  getLogger,
  setup,
} from "https://deno.land/std@0.224.0/log/mod.ts";
import * as path from "https://deno.land/std@0.224.0/path/mod.ts";
import {
  Command,
  EnumType,
} from "https://deno.land/x/cliffy@v1.0.0-rc.4/command/mod.ts";
import * as dax from "https://deno.land/x/dax@0.39.2/mod.ts";
import * as pgpass from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.14.9/lib/postgres/pgpass/pgpass-parse.ts";
import * as migrateIc from "./src/main/postgres/ingestion-center/migrations/migrations.ts";
import * as ddlTable from "./src/main/postgres/ingestion-center/migrations/models-dv.ts";

const $ = dax.build$({
  commandBuilder: new dax.CommandBuilder().noThrow(),
});

const exists = async (target: string | URL) =>
  await Deno.stat(target).then(() => true).catch(() => false);

const { conns: pgConns, issues: pgConnIssues } = await pgpass.parse(
  path.join(`${Deno.env.get("HOME")}`, ".pgpass"),
);
if (pgConnIssues.length > 0) {
  console.error("unexpected .pgpass issues", pgConnIssues);
}

const pgpassJdbcUrl = (
  connId: string,
  defaultValue = () => `jdbc:postgresql://unknown-conn-id-${connId}`,
) => {
  const conn = pgConns.find((conn) => conn.connDescr.id == connId);
  return conn
    ? `jdbc:postgresql://${conn.host}:${
      String(conn.port)
    }/${conn.database}?user=${conn.username}&password=${conn.password}`
    : defaultValue();
};

const pgpassPsqlArgs = (
  connId: string,
  defaultValue = () => `-h unknown-conn-id-${connId}`,
) => {
  const conn = pgConns.find((conn) => conn.connDescr.id == connId);
  return conn
    ? `-h ${conn.host} -p ${conn.port} -d ${conn.database} -U ${conn.username}`
    : defaultValue();
};

const schemaSpyArgs = (
  connId: string,
  defaultValue = () => `-host unknown-conn-id-${connId}`,
) => {
  const conn = pgConns.find((conn) => conn.connDescr.id == connId);
  return conn
    ? `-host ${conn.host} -port ${
      String(conn.port)
    } -db ${conn.database} -u ${conn.username} -p ${conn.password}`
    : defaultValue();
};

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

const cleanableTargetHome = "./target";
const cleanableTarget = (relative: string) =>
  path.join(cleanableTargetHome, relative);

// deno-fmt-ignore
const CLI = new Command()
  .name("UDI Control Plane")
  .version("0.1.0")
  .description("Universal Data Infrastructure (UDI) Orchestration")
  .command("clean", `Remove contents of ${cleanableTargetHome}`).action(() => {  
      try {
          Deno.removeSync(cleanableTargetHome, { recursive: true });
      } catch (_notFound) {
          // directory doesn't exist, it's OK
      }
  })
  .command("ic", new Command()
    .description("UDI Ingestion Center (IC) subject area commands handler")
    .command("generate", new Command()
      .description("Generate SQL and related artifacts")
      .command("sql", "Generate SQL artifacts")
        .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center") })
        .option("--destroy-fname <file-name:string>", "Filename of the generated destroy script in target", { default: "destroy.auto.psql" })
        .option("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver.auto.psql" })
        .option("--overwrite", "Don't remove existing target directory first, overwrite instead")
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
          let driverGenerateMigrationSQL:string = '';
          let destroySQL:string = '';
          (migrateIc.ic).forEach(async (module,index) => {
            const generated = await module.generated();
            driverGenerateMigrationSQL = driverGenerateMigrationSQL + '\n' + generated.driverGenerateMigrationSQL;
            destroySQL = destroySQL + '\n' +generated.destroySQL;
            if(module.migrationInput.description.length > 20){
              throw new Error('Migration version description `'+module.migrationInput.description+'` length cannot exceed 20 characters');
            }
            if(migrateIc.ic.length-1==index){
              Deno.writeTextFileSync(`${options.target}/${options.driverFname}`, driverGenerateMigrationSQL);
              Deno.writeTextFileSync(`${options.target}/${options.destroyFname}`, destroySQL);
              logger.debug(`${options.target}/${options.driverFname}`);
              logger.debug(`${options.target}/${options.destroyFname}`);
              [...generated.testDependencies].forEach((dep) => {
                const targetLocal = path.join(options.target, path.basename(dep));
                Deno.copyFileSync(toLocalPath(dep), targetLocal);
                logger.debug(targetLocal);
              });
            }
          });  
          
        })
      .command("java", new Command()
        .description("Generate Java code artifacts")
        .command("jooq", "Generate jOOQ code packages")
          .option("--build-dir <path:string>", "Destination for package", { default: cleanableTarget("/java/auto/jooq/ingress") })
          .option("--package <name:string>", "Java package name", { default: "org.techbd.udi.auto.jooq.ingress" })
          .option("--schema <name:string>", "Schema to inspect", { default: "techbd_udi_ingress" })
          .option("--jar <path:string>", "The JAR file to create", { default: "../hub-prime/lib/techbd-udi-jooq-ingress.auto.jar" })
          .option("-c, --conn-id <id:string>", "pgpass connection ID to use for JDBC URL", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
          .action(async (options) => {
            try {
                Deno.removeSync(options.buildDir, { recursive: true });
                Deno.removeSync(options.jar);
            } catch (_notFound) {
                // directory doesn't exist, it's OK
            }
            const jdbcURL = pgpassJdbcUrl(options.connId);
            await $`java -cp "./lib/*:./support/jooq/lib/*" support/jooq/JooqCodegen.java ${jdbcURL} ${options.schema} ${options.package} ${options.buildDir} ${options.jar}`;
            console.log("Java jOOQ generation complete, JAR file", options.jar);
            console.log("  ==> run `mvn clean compile` to freshen the cache");
          })
       )

      .command("docs", "Generate documentation artifacts")
        .option("--schemaspy-dest <path:string>", "Generate SchemaSpy documentation", { default: cleanableTarget("../../hub-prime/target/site/schemaSpy") })
        .option("-c, --conn-id <id:string>", "pgpass connection ID to use for SchemaSpy database credentials", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
        .option("--serve <port:number>", "Serve generated documentation at port")
        .action(async (options) => {
          const schemaSpyCreds = schemaSpyArgs(options.connId);
          await $.raw`java -jar ./lib/schemaspy-6.2.4.jar -t pgsql11 -dp ./lib/postgresql-42.7.3.jar -schemas techbd_udi_ingress,info_schema_lifecycle ${schemaSpyCreds} -debug -o ${options.schemaspyDest} -vizjs`;
          if(options.serve) {
              Deno.serve({ port: options.serve }, (req) => {
                return serveDir(req, {
                  fsRoot: options.schemaspyDest,
                });
              });
            }
        })
      .command("prepare-diagram", "Generate puml diagram")
        .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center") })
        .option("--puml-fname <file-name:string>", "Filename of the generated destroy script in target", { default: "ddl-table.auto.puml" })
        .option("--overwrite", "Don't remove existing target file first, overwrite instead")
        .action((options) => {
          if(!options.overwrite) {
            try {
                Deno.removeSync(options.target+'/'+options.pumlFname, { recursive: true });
            } catch (_notFound) {
                // directory doesn't exist, it's OK
            }
          }
          Deno.mkdirSync(options.target, { recursive: true });
          Deno.writeTextFileSync(`${options.target}/${options.pumlFname}`, ddlTable.generated().pumlERD);
        })        
    )
    .command("load-sql", "Use psql to execute generated migration scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center") })
      .option("--destroy-fname <file-name:string>", "Filename of the generated destroy script in target", { default: "destroy.auto.psql" })
      .option("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver.auto.psql" })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--destroy-first", "Destroy objects before migration")
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./udictl-load-sql-${new Date().toISOString()}.log` })
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .type("pg-client-min-messages-level", postreSqlClientMinMessagesLevelCliffyEnum)
      .option("-l, --psql-log-level <level:pg-client-min-messages-level>", "psql `client_min_messages` level.", {
        default: "warning",
      })
      .action(async (options) => {
        let psqlErrors = 0;
        const logger = setupLogger(options);
        const psqlCreds = pgpassPsqlArgs(options.connId);
        if(options.destroyFirst) {
            if(options.connId.indexOf("DESTROYABLE") == -1) {
                console.warn(`Skipping --destroy-first because --conn-id "${options.connId}" does not contain the word DESTROYABLE in the connection identifier.`);
                console.warn(`  --destroy-first is dangerous so be sure to name your identifier properly so you do not accidentally run in a non-sandbox database.`);
                Deno.exit(-1);
            }
            const psqlContentFName = `${options.target}/${options.destroyFname}`;
            if(!(await exists(psqlContentFName))) {
              console.warn(`${psqlContentFName} does not exist. Did you run 'generate sql' command?`);
              Deno.exit(-1);
            }
            const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${psqlContentFName}`.captureCombined();
            logger.debug(`-- DESTROYING FIRST WITH ${options.destroyFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`);
            logger.debug(psqlResults.combined);
            if(psqlResults.code != 0) {
              logger.debug(`ERROR non-zero error code ${options.psql} ${psqlResults.code}`);
              psqlErrors++;
            }
            logger.debug(`-- END ${options.destroyFname} at ${new Date()}\n\n`);
        }
        const psqlContentFName = `${options.target}/${options.driverFname}`;
        if(!(await exists(psqlContentFName))) {
          console.warn(`${psqlContentFName} does not exist. Did you run 'generate sql' command?`);
          Deno.exit(-1);
        }
        const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${psqlContentFName}`.captureCombined();
        logger.debug(`-- BEGIN ${options.driverFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`);
        logger.debug(psqlResults.combined);
        if(psqlResults.code != 0) {
          logger.debug(`ERROR non-zero error code ${options.psql} ${psqlResults.code}`);
          psqlErrors++;
        }
        logger.debug(`-- END ${options.driverFname} at ${new Date()}\n\n`);
        console.log("Load SQL complete, results logged in", options.logResults);
        if(psqlErrors) {
          console.error(`WARNING: ${psqlErrors} ${options.psql} error(s) occurred, see log file ${options.logResults}`);
        }
      })
    .command("test", "Use psql to execute pgTAP scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center") })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--suite-fname <file-name:string>", "Filename of the generated test suite script in target", { default: "suite.pgtap.psql" })
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./udictl-test-${new Date().toISOString()}.log` })
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .type("pg-client-min-messages-level", postreSqlClientMinMessagesLevelCliffyEnum)
      .option("-l, --psql-log-level <level:pg-client-min-messages-level>", "psql `client_min_messages` level.", {
        default: "warning",
      })
      .action(async (options) => {
        let psqlErrors = 0;
        const logger = setupLogger(options);
        const psqlCreds = pgpassPsqlArgs(options.connId);
        const psqlContentFName = `${options.target}/${options.suiteFname}`;
        if(!(await exists(psqlContentFName))) {
          console.warn(`${psqlContentFName} does not exist. Did you run 'generate sql' command?`);
          Deno.exit(-1);
        }
        const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${psqlContentFName}`.captureCombined();
        logger.debug(`-- BEGIN ${options.suiteFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`);
        logger.debug(psqlResults.combined);
        logger.debug(`-- END ${options.suiteFname} at ${new Date()}\n\n`);
        if(psqlResults.code != 0) {
          logger.debug(`ERROR non-zero error code ${options.psql} ${psqlResults.code}`);
          psqlErrors++;
        }
        console.log("Test complete, results logged in", options.logResults);
        if(psqlErrors) {
          console.error(`WARNING: ${psqlErrors} ${options.psql} error(s) occurred, see log file ${options.logResults}`);
        }
      })
    .command("migrate", "Use psql to generate migration scripts based on current state")
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .option("-l, --is-linted <id:string>", "migrate lint", { required: true, default: "true" })
      .action(async (options) => {
        const psqlCreds = pgpassPsqlArgs(options.connId);
        console.log((await $.raw`${options.psql} ${psqlCreds} -q -t -A -P border=0 -X -c "CALL info_schema_lifecycle.islm_migrate('info_schema_lifecycle',true,${options.isLinted});"`.captureCombined().lines()).join("\n"));
      })      
    .command("omnibus-fresh", "Freshen the given connection ID")
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .option("-l, --is-linted <id:string>", "migrate lint", { required: true, default: "true" })
      .option("--deploy-jar", "Deploy the generated JAR file after omnibus-fresh completes")
      .option("--jar <jar:string>", "Path to the JAR file to deploy (defaults to generated jOOQ JAR)")
      .option("-t, --targets <targets:string>", "Comma-separated list of target directories for JAR deployment (optional)")
      .option("--dry-run", "Show what would be copied without actually copying (for JAR deployment)")
      .option("-v, --verbose", "Enable verbose output (for JAR deployment)")
      .action(async (options) => {
        await CLI.parse(["ic", "generate", "sql"]);
        await CLI.parse(["ic", "load-sql", "--destroy-first", "--conn-id", options.connId]);
        await CLI.parse(["ic", "test", "--conn-id", options.connId]);
        await CLI.parse(["ic", "migrate", "--conn-id", options.connId, "--is-linted", options.isLinted]);
        await CLI.parse(["ic", "generate", "java", "jooq", "--conn-id", options.connId]);

        // Deploy JAR if requested
        if (options.deployJar) {
          console.log("\n?? Starting JAR deployment...");

          // Use provided JAR path or default to the generated jOOQ JAR
          const jarPath = options.jar || "../hub-prime/lib/techbd-udi-jooq-ingress.auto.jar";

          // Build deploy-jar command arguments
          const deployArgs = ["ic", "deploy-jar", "--jar", jarPath];

          if (options.targets) {
            deployArgs.push("--targets", options.targets);
          }

          if (options.dryRun) {
            deployArgs.push("--dry-run");
          }

          if (options.verbose) {
            deployArgs.push("--verbose");
          }

          await CLI.parse(deployArgs);
        }
      })
    .command("deploy-jar", new Command()
      .description("Deploy JAR file to multiple target lib directories")
      .option("-j, --jar <jar:string>", "Path to the JAR file to deploy", { required: true })
      .option("-t, --targets <targets:string>", "Comma-separated list of target directories (optional)")
      .option("--dry-run", "Show what would be copied without actually copying")
      .option("-v, --verbose", "Enable verbose output")
      .action(async (options) => {

        if (options.verbose) {
          console.log(`?? Options:`, options);
        }

        // Verify the source JAR file exists
        try {
          const jarStat = await Deno.stat(options.jar);
          if (options.verbose) {
            console.log(`?? Source JAR: ${options.jar} (${jarStat.size} bytes)`);
          }
        } catch (_error) {
          console.error(`? Error: JAR file not found: ${options.jar}`);
          Deno.exit(1);
        }

        // Define target directories
        const defaultTargetDirs = [
          "../nexus-core-lib/lib",
          "../csv-service/lib",
          "../fhir-validation-service/lib",
          "../core-lib/lib"
        ];

        const targetDirs = options.targets
          ? options.targets.split(",").map(dir => dir.trim())
          : defaultTargetDirs;

        if (options.verbose) {
          console.log(`?? Target directories:`, targetDirs);
        }

        if (options.dryRun) {
          console.log(`?? DRY RUN MODE - No files will be copied`);
        }

        let successCount = 0;
        let errorCount = 0;
        const results = [];

        // === Deploy the JAR to multiple target lib directories ===
        for (const targetDir of targetDirs) {
          try {
            const jarFileName = options.jar.split("/").pop();
            const targetPath = `${targetDir}/${jarFileName}`;

            if (options.dryRun) {
              console.log(`?? Would copy: ${options.jar} ? ${targetPath}`);

              // Check if target directory exists
              try {
                await Deno.stat(targetDir);
                console.log(`   ? Target directory exists: ${targetDir}`);
              } catch {
                console.log(`   ?? Target directory would be created: ${targetDir}`);
              }

              successCount++;
              results.push({ target: targetPath, status: "would-copy" });
              continue;
            }

            // Ensure the target directory exists
            await Deno.mkdir(targetDir, { recursive: true });

            if (options.verbose) {
              console.log(`?? Ensured directory exists: ${targetDir}`);
            }

            // Copy the file
            await Deno.copyFile(options.jar, targetPath);

            console.log(`? Successfully deployed to: ${targetPath}`);
            successCount++;
            results.push({ target: targetPath, status: "success" });

          } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            console.error(`? Failed to deploy to ${targetDir}: ${errorMessage}`);
            errorCount++;
            results.push({ target: `${targetDir}/${options.jar.split("/").pop()}`, status: "error", error: errorMessage });
          }
        }

        // === Summary Report ===
        console.log(`\n?? Deployment Summary:`);
        console.log(`   ? Successful: ${successCount}`);
        console.log(`   ? Failed: ${errorCount}`);
        console.log(`   ?? Total targets: ${targetDirs.length}`);

        if (options.dryRun) {
          console.log(`   ?? Mode: DRY RUN (no files copied)`);
        }

        if (options.verbose && results.length > 0) {
          console.log(`\n?? Detailed Results:`);
          results.forEach((result, index) => {
            const status = result.status === "success" ? "?" :
                          result.status === "would-copy" ? "??" : "?";
            console.log(`   ${index + 1}. ${status} ${result.target}`);
            if (result.error) {
              console.log(`      Error: ${result.error}`);
            }
          });
        }

        if (errorCount > 0) {
          console.log(`\n??  Some deployments failed. Check the errors above.`);
          Deno.exit(1);
        } else {
          console.log(`\n?? All deployments completed successfully!`);
        }
      }))
    );

await CLI.parse(Deno.args);
