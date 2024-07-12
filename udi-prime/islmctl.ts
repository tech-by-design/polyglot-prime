#!/usr/bin/env -S deno run --allow-all

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
import * as pgpass from "https://raw.githubusercontent.com/netspective-labs/sql-aide/v0.13.27/lib/postgres/pgpass/pgpass-parse.ts";
import * as im from "./src/main/postgres/ingestion-center/islm/migrate-update-table1.ts";

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
    .command("islm", new Command()
    .description("UDI Ingestion Center (IC) subject area commands handler")
    .command("generate", new Command()
      .description("Generate SQL and related artifacts")
      .command("sql", "Generate SQL artifacts")
        .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center/islm") })
        .option("--driver-load-islm-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver_islm_load_"+im.migrateVersion+".auto.psql" })
        .option("--driver-load-migration-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver_migration_load_"+im.migrateVersion+".auto.psql" })
        .option("--driver-migrate-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver_migrate_"+im.migrateVersion+".auto.psql" })
        .option("--driver-rollback-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver_rollback_"+im.migrateVersion+".auto.psql" })
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
          const generated = im.generated();
          Deno.writeTextFileSync(`${options.target}/${options.driverLoadIslmFname}`, generated.driverGenerateIslmSQL);
          logger.debug(`${options.target}/${options.driverLoadIslmFname}`);
          Deno.writeTextFileSync(`${options.target}/${options.driverLoadMigrationFname}`, generated.driverGenerateMigrationSQL);
          logger.debug(`${options.target}/${options.driverLoadMigrationFname}`);
          Deno.writeTextFileSync(`${options.target}/${options.driverMigrateFname}`, generated.driverMigrateSQL);
          logger.debug(`${options.target}/${options.driverMigrateFname}`);
          Deno.writeTextFileSync(`${options.target}/${options.driverRollbackFname}`, generated.driverRollbackSQL);
          logger.debug(`${options.target}/${options.driverRollbackFname}`);
          [...generated.testDependencies].forEach((dep) => {
            const targetLocal = path.join(options.target, path.basename(dep));
            Deno.copyFileSync(toLocalPath(dep), targetLocal);
            logger.debug(targetLocal);
          });
        })      
    )
    .command("bootstrap", "Use psql to execute generated migration scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center/islm") })
      .option("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver_islm_load_"+im.migrateVersion+".auto.psql" })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--destroy-first", "Destroy objects before migration")
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./islmctl-load-islm-${im.migrateVersion}-${new Date().toISOString()}.log` })
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .type("pg-client-min-messages-level", postreSqlClientMinMessagesLevelCliffyEnum)
      .option("-l, --psql-log-level <level:pg-client-min-messages-level>", "psql `client_min_messages` level.", {
        default: "warning",
      })
      .action(async (options) => {
        let psqlErrors = 0;
        const logger = setupLogger(options);
        const psqlCreds = pgpassPsqlArgs(options.connId);        
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
        console.log("ISLM loading complete, results logged in", options.logResults);
        if(psqlErrors) {
          console.error(`WARNING: ${psqlErrors} ${options.psql} error(s) occurred, see log file ${options.logResults}`);
        }
      })
    .command("test", "Use psql to execute pgTAP scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center/islm") })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--suite-fname <file-name:string>", "Filename of the generated test suite script in target", { default: "islm.pgtap.psql" })
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./islmctl-test-islm-${im.migrateVersion}-${new Date().toISOString()}.log` })
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
        console.log("ISLM test complete, results logged in", options.logResults);
        if(psqlErrors) {
          console.error(`WARNING: ${psqlErrors} ${options.psql} error(s) occurred, see log file ${options.logResults}`);
        }
      })  
    .command("load-migrate-sql", "Use psql to execute generated migration scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center/islm") })
      .option("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver_migration_load_"+im.migrateVersion+".auto.psql" })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--destroy-first", "Destroy objects before migration")
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./islmctl-load-migration-${im.migrateVersion}-${new Date().toISOString()}.log` })
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .type("pg-client-min-messages-level", postreSqlClientMinMessagesLevelCliffyEnum)
      .option("-l, --psql-log-level <level:pg-client-min-messages-level>", "psql `client_min_messages` level.", {
        default: "warning",
      })
      .action(async (options) => {
        let psqlErrors = 0;
        const logger = setupLogger(options);
        const psqlCreds = pgpassPsqlArgs(options.connId);        
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
        console.log("Load migration procedures complete, results logged in", options.logResults);
        if(psqlErrors) {
          console.error(`WARNING: ${psqlErrors} ${options.psql} error(s) occurred, see log file ${options.logResults}`);
        }
      })         
    .command("migrate", "Use psql to execute generated migration scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center/islm") })
      .option("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver_migrate_"+im.migrateVersion+".auto.psql" })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--destroy-first", "Destroy objects before migration")
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./islmctl-migrate-${im.migrateVersion}-${new Date().toISOString()}.log` })
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .type("pg-client-min-messages-level", postreSqlClientMinMessagesLevelCliffyEnum)
      .option("-l, --psql-log-level <level:pg-client-min-messages-level>", "psql `client_min_messages` level.", {
        default: "warning",
      })
      .action(async (options) => {
        let psqlErrors = 0;
        const logger = setupLogger(options);
        const psqlCreds = pgpassPsqlArgs(options.connId);        
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
        console.log("Migration complete, results logged in", options.logResults);
        if(psqlErrors) {
          console.error(`WARNING: ${psqlErrors} ${options.psql} error(s) occurred, see log file ${options.logResults}`);
        }
      })
    .command("rollback", "Use psql to execute generated migration scripts")
      .option("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: cleanableTarget("/postgres/ingestion-center/islm") })
      .option("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver_rollback_"+im.migrateVersion+".auto.psql" })
      .option("--psql <path:string>", "`psql` command", { required: true, default: "psql" })
      .option("--destroy-first", "Destroy objects before migration")
      .option("--log-results <path:string>", "Store `psql` results in this log file", { default: `./islmctl-rollback-${im.migrateVersion}-${new Date().toISOString()}.log` })
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .type("pg-client-min-messages-level", postreSqlClientMinMessagesLevelCliffyEnum)
      .option("-l, --psql-log-level <level:pg-client-min-messages-level>", "psql `client_min_messages` level.", {
        default: "warning",
      })
      .action(async (options) => {
        let psqlErrors = 0;
        const logger = setupLogger(options);
        const psqlCreds = pgpassPsqlArgs(options.connId);        
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
        console.log("Rollback complete, results logged in", options.logResults);
        if(psqlErrors) {
          console.error(`WARNING: ${psqlErrors} ${options.psql} error(s) occurred, see log file ${options.logResults}`);
        }
      })
    .command("islm-bootstrap", "Create a fresh ISLM infrastructure with the given connection ID")
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .action(async (options) => {
        await CLI.parse(["islm", "generate", "sql"]);
        await CLI.parse(["islm", "bootstrap", "--conn-id", options.connId]);
        await CLI.parse(["islm", "test", "--conn-id", options.connId]);
      })
    .command("islm-migrate", "Migrate the SQL with the given connection ID")
      .option("-c, --conn-id <id:string>", "pgpass connection ID to use for psql", { required: true, default: "UDI_PRIME_DESTROYABLE_DEVL" })
      .action(async (options) => {
        await CLI.parse(["islm", "load-migrate-sql", "--conn-id", options.connId]);
        await CLI.parse(["islm", "migrate", "--conn-id", options.connId]);
      })    
    );

await CLI.parse(Deno.args);
