# AskDB

AskDB is a lightweight dashboard that lets a non-technical manager ask a relational database questions in plain English. The backend turns a supported question into SQL, runs it against either the seeded mock database or the currently connected remote database, and returns a table plus the generated SQL.

---

## 🚨 The Problem

Most business data lives inside relational databases — but the people who need it most can't access it.

Non-technical managers rely on engineers or analysts to run queries for them. That means:

- ⏳ **Waiting hours (or days)** for a simple data pull
- 🔁 **Bottlenecking technical teams** with repetitive one-off requests
- 📉 **Decisions made on stale data** because asking again feels like too much friction
- 🔒 **Data stays siloed** — the people closest to the business can't self-serve

The result: organisations sit on goldmines of data that only a handful of people can actually use.

---

## ✅ The Solution

AskDB bridges the gap between business questions and database answers — no SQL required.

A manager types: *"Show me the top 10 customers by revenue who haven't ordered in 30 days"*

AskDB:
1. Translates that into the correct SQL (handling JOINs, subqueries, filters)
2. Runs it against the connected database
3. Returns a clean data table — instantly

---

## ⚡ Core Features

- 🧠 **Natural language to SQL** — type a question in plain English, get SQL generated automatically via OpenAI
- 🗄️ **Mock database included** — normalized 4-table schema (customers, orders, products, order items) seeded and ready to query out of the box
- 🔌 **Remote database support** — connect PostgreSQL, MySQL, or SQL Server with your own credentials
- 📋 **SQL preview** — see the generated SQL alongside your results so technical users can verify or learn
- 🔄 **Follow-up queries** — refine results conversationally without starting over
- 📁 **Query history** — every query is logged with timestamp, database, and results

---

## 🚀 Project Structure

```text
AskDB/
  package.json        One-command dev scripts
  scripts/dev.mjs     Starts backend and frontend together
  frontend/           React + Vite dashboard
  backend/            Dependency-light Java API, mock database, and JDBC remote database connector
```

---

## 🛠️ Run Both Frontend and Backend Together

From the main `AskDB` folder:

```bash
npm run dev
```

That single command will:

1. Check that Java and `javac` are installed
2. Run `npm install` inside `frontend/` if `frontend/node_modules` is missing
3. Start the Java backend on `http://localhost:9090`, or the next free port if `9090` is busy
4. Start the Vite frontend on `http://localhost:5173`

Then open:

```text
http://localhost:5173
```

If port `9090` is already taken, `npm run dev` automatically tries the next free port and points the frontend proxy to it. You can still force a specific backend port:

```bash
BACKEND_PORT=9091 npm run dev
```

---

## 🔀 Run Separately

| Command | What it does |
|---|---|
| `npm run backend` | Start Java backend only |
| `npm run frontend` | Start Vite frontend only |
| `npm run build` | Build frontend for production |
| `npm run preview` | Preview the production build |

---

## 🌐 API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/api/health` | GET | Backend status |
| `/api/schema` | GET | Active database schema |
| `/api/connect` | POST | Validate and store a database connection |
| `/api/query` | POST | Convert plain-English question to SQL and return rows |
| `/api/history` | GET | In-memory query history |

---

## 🔌 Remote Database Connections

Supported databases:
- **MockDB** (built-in, no setup needed)
- **PostgreSQL**
- **MySQL**
- **SQL Server**

When the backend starts, `backend/run.sh` and `backend/run.bat` run `scripts/fetch-jdbc.mjs` to download JDBC drivers into `backend/lib/`. Those jars are git-ignored. If the download is skipped or offline, MockDB still works — remote connections will return a driver error until drivers are available.

For remote databases, `/api/connect` opens a real JDBC connection with the submitted host, port, database name, username, and password. On success, AskDB stores that connection in memory and refreshes `/api/schema` from database metadata. On failure, it returns an error instead of pretending the connection worked.

Remote queries are **read-only**: the backend only runs generated `SELECT` or `WITH` SQL and rejects multiple statements.

---

## 🤖 AI SQL Generation

For remote databases, AskDB uses OpenAI to translate arbitrary English questions into SQL for the active schema.

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

---

## 💬 Sample Questions

```
Show me the top 10 customers by revenue who haven't ordered in 30 days
List top customers by revenue
Show recent orders
Which products generated the most revenue?
```

---

## 🗺️ User Flow

```
User opens dashboard
       ↓
User connects a database (MockDB or remote)
       ↓
User types a question in plain English
       ↓
AskDB converts the question into SQL via OpenAI
       ↓
AskDB queries the database with that SQL
       ↓
AskDB displays the results as a data table
```

---

## 📌 Next Steps

The remote JDBC executor is now in place. The next major upgrade is replacing the rule-based SQL generator with an LLM-backed generator that can dynamically generate SQL from any connected database's schema.
