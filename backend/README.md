# AskDB Backend

Dependency-light Java HTTP API for the AskDB MVP. It supports the bundled MockDB and remote JDBC connections for PostgreSQL, MySQL, and SQL Server.

## Recommended dev command

Run the full app from the root `AskDB` folder:

```bash
npm run dev
```

That starts both the backend and the frontend together.

## Backend-only command

```bash
./run.sh
```

The backend runs at `http://localhost:9090` by default.

On startup, the script downloads JDBC driver jars into `backend/lib/` when they are missing. MockDB continues to work without those jars, but remote database connections need them.

Use a different port with:

```bash
PORT=9091 ./run.sh
```

Windows users can run:

```bat
run.bat
```
