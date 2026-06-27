# AskDB

AskDB is a lightweight dashboard that lets a non-technical manager ask a relational database questions in plain English. The backend turns a supported question into SQL, runs it against a seeded mock database and returns a table plus the generated SQL.

## Project structure

```text
AskDB/
  package.json        One-command dev scripts
  scripts/dev.mjs     Starts backend and frontend together
  frontend/           React + Vite dashboard
  backend/            Dependency-light Java API and mock relational database
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
- `GET /api/schema` - mock database schema
- `POST /api/connect` - accepts connection fields and returns a mock connection
- `POST /api/query` - turns a plain-English question into SQL and returns rows
- `GET /api/history` - in-memory query history

## Supported sample questions

- `Show me the top 10 customers by revenue who haven't ordered in 30 days`
- `List top customers by revenue`
- `Show recent orders`
- `Which products generated the most revenue?`

## What was fixed earlier

The original repo had two competing frontend setups: one in the repo root and another in `frontend/`. It also included a checked-in `node_modules/` folder from another machine. That made Vite fail with missing native optional packages such as `@rolldown/binding-linux-x64-gnu`.

This fixed version keeps one frontend in `frontend/`, keeps one backend in `backend/`, removes generated dependency/build folders from source control and adds a `.gitignore` so the same problem does not come back.

## Next production steps

To connect a real database, add JDBC drivers and credentials, replace the mock database executor with a real read-only SQL executor and replace the rule-based SQL generator with an LLM-backed generator or a stronger parser.
