Each "migratable" SQL script file has the following format:

`<nnnn>_<shortID>-any-text.<dialect>.sql` such as
`001_WyevQ9E6L-seed-ddl.duckdb.sql` or `002_IDEMPOTENT-seed-dml.duckdb.sql`.

- `nnnn` should be numeric starting with `0001`
- `shortId` is a unique random 9 character alphanumeric ID like `WyevQ9E6L`
  which _must not contain hyphens_ (see
  [shortunique.id](https://shortunique.id/) to generate).
  - The `shortId` is important because it will be used to manage _state_ of this
    script and will only be run if it's not been executed before.
  - `shortId` can be set to `IDEMPOTENT` to indicate that the script can be run
    each time since the results are _idempotent_.
    - For example, `002_IDEMPOTENT-seed-dml.duckdb.sql` would mean that the SQL
      DML in this file can be run safely multiple times because it inserts data
      but uses `ON CONFLICT IGNORE` to remain _idempotent_.
- `any-text` is human description
- `dialect` is `duckdb`, `sqlite`, `postgres`, etc. meant for humans
- `.sql` is static meaning it's an ANSI SQL script

When the connection is established, the scripts will be run sorted by `nnnn`
(e.g. `0001` then `0002`, etc.).

TODO: at this time all scripts are run but we need to investigate a simple but
effective way of managing state (perhaps like `surveilr` does).
