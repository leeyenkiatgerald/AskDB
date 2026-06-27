# AskDB

AskDB is a lightweight dashboard that lets a non-technical manager ask a relational database questions in plain English. The backend turns a supported question into SQL, runs it against either the seeded mock database or the currently connected remote database, and returns a table plus the generated SQL.

## Project structure

```text
AskDB/
  package.json        One-command dev scripts
  scripts/dev.mjs     Starts backend and frontend together
  frontend/           React + Vite dashboard
  backend/            Dependency-light Java API, mock database, and JDBC remote database connector
```

## Run both frontend and backend together

From the main `AskDB` folder:

```bash
npm run dev
```

That single command will:

1. check that Java and `javac` are installed
2. run `npm install` inside `frontend/` if `frontend/node_modules` is missing
3. start the Java backend on `http://localhost:9090`, or the next free port if `9090` is busy
4. start the Vite frontend on `http://localhost:5173`

Then open:

```text
http://localhost:5173
```

If port `9090` is already taken, `npm run dev` automatically tries the next free port and points the frontend proxy to it. You can still force a specific backend port:

```bash
BACKEND_PORT=9091 npm run dev
```

## Run them separately

Backend only:

```bash
npm run backend
```

Frontend only:

```bash
npm run frontend
```

Build frontend:

```bash
npm run build
```

Preview frontend build:

```bash
npm run preview
```

## API endpoints

- `GET /api/health` - backend status
- `GET /api/schema` - active database schema
- `POST /api/connect` - validates and stores the active MockDB or remote database connection
- `POST /api/query` - turns a plain-English question into SQL and returns rows from the active database
- `GET /api/history` - in-memory query history

## Remote database connections

The connection page supports:

- MockDB
- PostgreSQL
- MySQL
- SQL Server

When the backend starts, `backend/run.sh` and `backend/run.bat` run `scripts/fetch-jdbc.mjs` to download JDBC drivers into `backend/lib/`. Those jars are ignored by git. If the download is skipped or offline, MockDB still works, but remote connection attempts will return a driver error.

For remote databases, `/api/connect` now opens a real JDBC connection with the submitted host, port, database name, username, and password. On success, AskDB stores that connection configuration in memory and refreshes `/api/schema` from database metadata. On failure, it returns an error instead of pretending the connection worked.

Remote queries are read-only: the backend only runs generated `SELECT` or `WITH` SQL and rejects multiple statements.

## AI SQL generation

For remote databases, AskDB can use OpenAI to translate arbitrary English questions into SQL for the active schema.

Set your API key before starting the app:

```bash
export OPENAI_API_KEY="your_openai_api_key"
npm run dev
```

Optional model override:

```bash
export OPENAI_MODEL="gpt-4o-mini"
```

When `OPENAI_API_KEY` is not set, remote databases fall back to the built-in rule-based generator. MockDB always uses the built-in demo generator because it is backed by an in-memory Java executor rather than a real SQL engine.

## Supported sample questions

- `Show me the top 10 customers by revenue who haven't ordered in 30 days`
- `List top customers by revenue`
- `Show recent orders`
- `Which products generated the most revenue?`

## What was fixed earlier

The original repo had two competing frontend setups: one in the repo root and another in `frontend/`. It also included a checked-in `node_modules/` folder from another machine. That made Vite fail with missing native optional packages such as `@rolldown/binding-linux-x64-gnu`.

This fixed version keeps one frontend in `frontend/`, keeps one backend in `backend/`, removes generated dependency/build folders from source control and adds a `.gitignore` so the same problem does not come back.

## Next production steps

The remote JDBC executor is now in place. The next major upgrade is replacing the rule-based SQL generator with an LLM-backed generator or stronger parser that can generate SQL from each connected database schema.
