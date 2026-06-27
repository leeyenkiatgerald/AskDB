import { useEffect, useMemo, useState } from 'react'
import {
  connectDatabase,
  getHistory,
  getSchema,
  healthCheck,
  runQuery,
} from './api.js'
import './App.css'

const sampleQuestions = [
  "Show me the top 10 customers by revenue who haven't ordered in 30 days",
  'List top customers by revenue',
  'Show recent orders',
  'Which products generated the most revenue?',
]

const emptyConnection = {
  dbType: 'MockDB',
  host: 'localhost',
  port: '0',
  databaseName: 'AskDB Demo Warehouse',
  username: 'demo_manager',
}

function App() {
  const [status, setStatus] = useState({ ok: false, message: 'Checking backend...' })
  const [connection, setConnection] = useState(emptyConnection)
  const [connectedDb, setConnectedDb] = useState(null)
  const [schema, setSchema] = useState([])
  const [history, setHistory] = useState([])
  const [question, setQuestion] = useState(sampleQuestions[0])
  const [result, setResult] = useState(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    async function boot() {
      try {
        const [health, schemaResponse, historyResponse] = await Promise.all([
          healthCheck(),
          getSchema(),
          getHistory(),
        ])
        setStatus({ ok: true, message: health.message })
        setSchema(schemaResponse.tables || [])
        setHistory(historyResponse.items || [])
      } catch (err) {
        setStatus({ ok: false, message: 'Backend offline. Start the Java server on port 8080.' })
        setError(err.message)
      }
    }
    boot()
  }, [])

  const columns = useMemo(() => result?.columns || [], [result])

  async function handleConnect(event) {
    event.preventDefault()
    setError('')
    try {
      const response = await connectDatabase(connection)
      setConnectedDb(response.connection)
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleRunQuery(event) {
    event.preventDefault()
    if (!question.trim()) return

    setIsLoading(true)
    setError('')
    try {
      const response = await runQuery({
        question,
        databaseName: connectedDb?.databaseName || connection.databaseName,
        previousSql: result?.sql || '',
      })
      setResult(response)
      const historyResponse = await getHistory()
      setHistory(historyResponse.items || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setIsLoading(false)
    }
  }

  function useHistoryItem(item) {
    setQuestion(item.question)
    setResult(item)
  }

  return (
    <main className="app-shell">
      <header className="hero-card">
        <div>
          <p className="eyebrow">AskDB MVP</p>
          <h1>Ask your database in plain English.</h1>
          <p className="hero-copy">
            A lightweight dashboard for managers who need quick answers from normalized
            relational data without writing joins, filters or SQL by hand.
          </p>
        </div>
        <div className="status-card">
          <span className={status.ok ? 'status-dot online' : 'status-dot offline'} />
          <div>
            <strong>{status.ok ? 'Backend connected' : 'Backend not connected'}</strong>
            <p>{status.message}</p>
          </div>
        </div>
      </header>

      {error && <div className="alert">{error}</div>}

      <section className="grid two-columns">
        <section className="panel">
          <div className="section-heading">
            <p className="eyebrow">Step 1</p>
            <h2>Connect database</h2>
          </div>
          <form className="connection-form" onSubmit={handleConnect}>
            <label>
              Type
              <select
                value={connection.dbType}
                onChange={(event) => setConnection({ ...connection, dbType: event.target.value })}
              >
                <option>MockDB</option>
                <option>PostgreSQL</option>
                <option>MySQL</option>
                <option>SQL Server</option>
              </select>
            </label>
            <label>
              Host
              <input
                value={connection.host}
                onChange={(event) => setConnection({ ...connection, host: event.target.value })}
              />
            </label>
            <label>
              Port
              <input
                value={connection.port}
                onChange={(event) => setConnection({ ...connection, port: event.target.value })}
              />
            </label>
            <label>
              Database
              <input
                value={connection.databaseName}
                onChange={(event) => setConnection({ ...connection, databaseName: event.target.value })}
              />
            </label>
            <label>
              User
              <input
                value={connection.username}
                onChange={(event) => setConnection({ ...connection, username: event.target.value })}
              />
            </label>
            <button className="primary-button" type="submit">
              Connect mock database
            </button>
          </form>
          {connectedDb && (
            <div className="connected-box">
              Connected to <strong>{connectedDb.databaseName}</strong> as {connectedDb.username}
            </div>
          )}
        </section>

        <section className="panel schema-panel">
          <div className="section-heading">
            <p className="eyebrow">Schema</p>
            <h2>Mock relational database</h2>
          </div>
          <div className="schema-list">
            {schema.map((table) => (
              <article key={table.name} className="schema-table">
                <strong>{table.name}</strong>
                <p>{table.description}</p>
                <code>{table.columns.join(', ')}</code>
              </article>
            ))}
          </div>
        </section>
      </section>

      <section className="panel query-panel">
        <div className="section-heading">
          <p className="eyebrow">Step 2</p>
          <h2>Query in English</h2>
        </div>
        <form className="query-form" onSubmit={handleRunQuery}>
          <textarea
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="Example: Show me the top 10 customers by revenue who haven't ordered in 30 days"
          />
          <button className="primary-button" type="submit" disabled={isLoading}>
            {isLoading ? 'Asking database...' : 'Generate SQL and run'}
          </button>
        </form>
        <div className="samples">
          {sampleQuestions.map((sample) => (
            <button key={sample} type="button" onClick={() => setQuestion(sample)}>
              {sample}
            </button>
          ))}
        </div>
      </section>

      {result && (
        <section className="grid result-grid">
          <section className="panel sql-panel">
            <div className="section-heading">
              <p className="eyebrow">Generated SQL</p>
              <h2>{result.summary}</h2>
            </div>
            <pre>{result.sql}</pre>
          </section>

          <section className="panel table-panel">
            <div className="section-heading">
              <p className="eyebrow">Results</p>
              <h2>{result.rowCount} rows returned</h2>
            </div>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    {columns.map((column) => (
                      <th key={column}>{column}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {result.rows.map((row, index) => (
                    <tr key={`${row.id || row.customer_id || row.product_id || 'row'}-${index}`}>
                      {columns.map((column) => (
                        <td key={column}>{String(row[column] ?? '')}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </section>
      )}

      <section className="panel history-panel">
        <div className="section-heading">
          <p className="eyebrow">Extension</p>
          <h2>Query history</h2>
        </div>
        {history.length === 0 ? (
          <p className="muted">No queries yet. Run a question to save it here.</p>
        ) : (
          <div className="history-list">
            {history.map((item) => (
              <button key={item.id} type="button" onClick={() => useHistoryItem(item)}>
                <span>{item.question}</span>
                <small>{item.createdAt}</small>
              </button>
            ))}
          </div>
        )}
      </section>
    </main>
  )
}

export default App
