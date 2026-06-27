import { createWriteStream, existsSync, mkdirSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import https from 'node:https'

const rootDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = join(rootDir, '..')
const libDir = join(projectRoot, 'backend', 'lib')

const drivers = [
  {
    name: 'PostgreSQL JDBC',
    file: 'postgresql-42.7.11.jar',
    url: 'https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.11/postgresql-42.7.11.jar',
  },
  {
    name: 'MySQL Connector/J',
    file: 'mysql-connector-j-9.7.0.jar',
    url: 'https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar',
  },
  {
    name: 'Microsoft JDBC Driver for SQL Server',
    file: 'mssql-jdbc-13.4.0.jre11.jar',
    url: 'https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.4.0.jre11/mssql-jdbc-13.4.0.jre11.jar',
  },
]

if (process.env.ASKDB_SKIP_JDBC_DOWNLOAD === '1') {
  process.exit(0)
}

mkdirSync(libDir, { recursive: true })

for (const driver of drivers) {
  const target = join(libDir, driver.file)
  if (existsSync(target) && statSync(target).size > 0) {
    continue
  }

  try {
    console.log(`[setup] Downloading ${driver.name}`)
    await download(driver.url, target)
  } catch (error) {
    console.warn(`[setup] Could not download ${driver.name}: ${error.message}`)
    console.warn('[setup] MockDB will still work. Remote connections need JDBC jars in backend/lib.')
  }
}

function download(url, target) {
  return new Promise((resolve, reject) => {
    const request = https.get(url, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        response.resume()
        download(response.headers.location, target).then(resolve, reject)
        return
      }

      if (response.statusCode !== 200) {
        response.resume()
        reject(new Error(`HTTP ${response.statusCode}`))
        return
      }

      const file = createWriteStream(target)
      response.pipe(file)
      file.on('finish', () => {
        file.close(resolve)
      })
      file.on('error', reject)
    })

    request.setTimeout(30000, () => {
      request.destroy(new Error('Timed out'))
    })
    request.on('error', reject)
  })
}
