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
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const [activeView, setActiveView] = useState('query')

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
  const databaseLabel = connectedDb?.databaseName || connection.databaseName

  async function handleConnect(event) {
    event.preventDefault()
    setError('')
    try {
      const response = await connectDatabase(connection)
      setConnectedDb(response.connection)
      setActiveView('query')
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
        databaseName: databaseLabel,
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
    setActiveView('query')
  }

  function startNewQuery() {
    setQuestion('')
    setResult(null)
    setActiveView('query')
  }

  return (
    <main className="app-frame">
      <aside className="sidebar">
        <div className="sidebar-top">
          <button className="brand-button" type="button" onClick={startNewQuery}>
            <span className="brand-mark">A</span>
            <span>AskDB</span>
          </button>

          <button
            className={activeView === 'connect' ? 'nav-button active' : 'nav-button'}
            type="button"
            onClick={() => setActiveView('connect')}
          >
            <span aria-hidden="true">+</span>
            <span>Connect database</span>
          </button>

          <button
            className={activeView === 'query' ? 'nav-button active' : 'nav-button'}
            type="button"
            onClick={startNewQuery}
          >
            <span aria-hidden="true">Q</span>
            <span>New query</span>
          </button>
        </div>

        <div className="sidebar-bottom">
          <div className="history-heading">
            <span>Query history</span>
            <small>{history.length}</small>
          </div>
          {history.length === 0 ? (
            <p className="history-empty">No queries yet.</p>
          ) : (
            <div className="side-history-list">
              {history.map((item) => (
                <button key={item.id} type="button" onClick={() => useHistoryItem(item)}>
                  <span>{item.question}</span>
                  <small>{item.createdAt}</small>
                </button>
              ))}
            </div>
          )}
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div className="connection-pill">
            <span className={status.ok ? 'status-dot online' : 'status-dot offline'} />
            <span>{status.ok ? databaseLabel : 'Backend offline'}</span>
          </div>
        </header>

        {error && <div className="alert">{error}</div>}

        {activeView === 'connect' ? (
          <section className="connect-page" aria-labelledby="connect-title">
            <div className="page-heading">
              <p className="eyebrow">Database</p>
              <h1 id="connect-title">Connect your database</h1>
              <p>Set up the source AskDB should query. The demo connection works with the bundled mock database.</p>
            </div>

            <div className="connect-layout">
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
                  Connect database
                </button>
              </form>

              <aside className="schema-panel">
                <div className="panel-heading">
                  <h2>Available schema</h2>
                  <p>{status.message}</p>
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
              </aside>
            </div>
          </section>
        ) : (
          <section className={result ? 'query-page has-result' : 'query-page'}>
            {!result && (
              <div className="query-hero">
                <h1>What would you like to know?</h1>
                <p>Ask a question in plain English and AskDB will generate SQL for {databaseLabel}.</p>
              </div>
            )}

            {result && (
              <section className="result-area">
                <div className="result-summary">
                  <p className="eyebrow">Generated SQL</p>
                  <h2>{result.summary}</h2>
                  <pre>{result.sql}</pre>
                </div>

                <div className="table-panel">
                  <div className="panel-heading">
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
                </div>
              </section>
            )}

            <form className="center-query-form" onSubmit={handleRunQuery}>
              <button className="query-icon-button" type="button" onClick={startNewQuery} aria-label="New query">
                +
              </button>
              <input
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                placeholder="Ask your database anything"
                aria-label="Ask your database anything"
              />
              <button className="submit-query" type="submit" disabled={isLoading || !question.trim()}>
                {isLoading ? '...' : 'Ask'}
              </button>
            </form>

            {!result && (
              <div className="samples">
                {sampleQuestions.map((sample) => (
                  <button key={sample} type="button" onClick={() => setQuestion(sample)}>
                    {sample}
                  </button>
                ))}
              </div>
            )}
          </section>
        )}
      </section>
    </main>
  )
}

export default App
