#!/usr/bin/env -S deno run --allow-all
import * as path from "https://deno.land/std@0.224.0/path/mod.ts";
import $ from "https://deno.land/x/dax@0.39.2/mod.ts";
import {
  Command,
  EnumType,
} from "https://deno.land/x/cliffy@v1.0.0-rc.4/command/mod.ts";
import * as ic from "./src/main/postgres/ingestion-center/mod.ts";

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

// deno-fmt-ignore
await new Command()
  .name("UDI Control Plane")
  .version("0.1.0")
  .description("Universal Data Infrastructure (UDI) Orchestration")
  .command("ic", new Command()
    .description("UDI Ingestion Center (IC) subject area commands handler")
    .globalOption("-t, --target <path:string>", "Target location for generated artifacts", { required: true, default: "./target/main/postgres/ingestion-center" })
    .globalOption("--destroy-fname <file-name:string>", "Filename of the generated destroy script in target", { default: "destroy.auto.psql" })
    .globalOption("--driver-fname <file-name:string>", "Filename of the generated construct script in target", { default: "driver.auto.psql" })
    .command("generate", "Generate SQL and related artifacts")
      .option("--overwrite", "Don't remove existing target directory first, overwrite instead")
      .action((options) => {
        if(!options.overwrite) {
            try {
                Deno.removeSync(options.target, { recursive: true });
            } catch (_notFound) {
                // directory doesn't exist, it's OK
            }
        }
        Deno.mkdirSync(options.target, { recursive: true });
        const generated = ic.generated();
        Deno.writeTextFile(`${options.target}/${options.driverFname}`, generated.driverSQL);
        Deno.writeTextFile(`${options.target}/${options.destroyFname}`, generated.destroySQL);
        [...generated.dependencies, ...generated.testDependencies].forEach((dep) => {
          Deno.copyFileSync(toLocalPath(dep), `${options.target}/${path.basename(dep)}`);
        });
      })
    .command("migrate", "Use psql to execute generated migration scripts")
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
        const psqlCreds = await $`${options.pgpass} psql-fmt --conn-id=${options.connId}`.text();
        if(options.destroyFirst) {
            const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${options.target}/${options.destroyFname}`.captureCombined();
            Deno.writeTextFile(options.logResults, `-- DESTROYING FIRST WITH ${options.destroyFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`, { append: true });
            Deno.writeTextFile(options.logResults, psqlResults.combined, { append: true });
            Deno.writeTextFile(options.logResults, `-- END ${options.destroyFname} at ${new Date()}\n\n`, { append: true });
        }
        const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${options.target}/${options.driverFname}`.captureCombined();
        Deno.writeTextFile(options.logResults, `-- BEGIN ${options.driverFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`, { append: true });
        Deno.writeTextFile(options.logResults, psqlResults.combined, { append: true });
        Deno.writeTextFile(options.logResults, `-- END ${options.driverFname} at ${new Date()}\n\n`, { append: true });
        console.log("Migration complete, results logged in", options.logResults);
      })
    .command("test", "Use psql to execute pgTAP scripts")
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
        const psqlCreds = await $`${options.pgpass} psql-fmt --conn-id=${options.connId}`.text();
        const psqlResults = await $.raw`${options.psql} ${psqlCreds} -c "${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}" -f ${options.target}/${options.suiteFname}`.captureCombined();
        Deno.writeTextFile(options.logResults, `-- BEGIN ${options.suiteFname} at ${new Date()}\n${postgreSqlClientMinMessagesSql(options.psqlLogLevel)}\n`, { append: true });
        Deno.writeTextFile(options.logResults, psqlResults.combined, { append: true });
        Deno.writeTextFile(options.logResults, `-- END ${options.suiteFname} at ${new Date()}\n\n`, { append: true });
        console.log("Test complete, results logged in", options.logResults);
      })
    )
  .parse(Deno.args);
