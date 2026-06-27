import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import net from 'node:net';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = join(rootDir, '..');
const frontendDir = join(projectRoot, 'frontend');
const backendDir = join(projectRoot, 'backend');
const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const javaCmd = process.platform === 'win32' ? 'java.exe' : 'java';
const javacCmd = process.platform === 'win32' ? 'javac.exe' : 'javac';
const preferredBackendPort = Number.parseInt(process.env.BACKEND_PORT || process.env.PORT || '9090', 10);
const portWasExplicit = Boolean(process.env.BACKEND_PORT || process.env.PORT);
const children = [];
let shuttingDown = false;

function checkCommand(command, label) {
  const result = spawnSync(command, ['-version'], { stdio: 'ignore' });
  if (result.error) {
    console.error(`Missing ${label}. Please install it before running AskDB.`);
    process.exit(1);
  }
}

function runBlocking(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, stdio: 'inherit', shell: false });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function canUsePort(port) {
  return new Promise((resolve) => {
    const server = net.createServer();

    server.once('error', () => resolve(false));
    server.once('listening', () => {
      server.close(() => resolve(true));
    });

    server.listen(port, '127.0.0.1');
  });
}

async function pickBackendPort() {
  if (!Number.isInteger(preferredBackendPort) || preferredBackendPort < 1 || preferredBackendPort > 65535) {
    console.error('Invalid backend port. Use BACKEND_PORT=9091 npm run dev with a port from 1 to 65535.');
    process.exit(1);
  }

  if (await canUsePort(preferredBackendPort)) {
    return preferredBackendPort;
  }

  if (portWasExplicit) {
    console.error(`Backend port ${preferredBackendPort} is already in use.`);
    console.error(`Stop the process using it or run with another port, for example: BACKEND_PORT=${preferredBackendPort + 1} npm run dev`);
    process.exit(1);
  }

  for (let port = preferredBackendPort + 1; port <= preferredBackendPort + 20; port += 1) {
    if (await canUsePort(port)) {
      console.warn(`[setup] Backend port ${preferredBackendPort} is busy. Using ${port} instead.`);
      return port;
    }
  }

  console.error(`Could not find a free backend port from ${preferredBackendPort} to ${preferredBackendPort + 20}.`);
  console.error('Stop an old backend process, then run npm run dev again.');
  process.exit(1);
}

function startProcess(name, command, args, cwd, env = process.env) {
  const child = spawn(command, args, { cwd, env, stdio: 'pipe', shell: false });
  children.push(child);

  child.stdout.on('data', (chunk) => {
    process.stdout.write(`[${name}] ${chunk}`);
  });

  child.stderr.on('data', (chunk) => {
    process.stderr.write(`[${name}] ${chunk}`);
  });

  child.on('exit', (code, signal) => {
    if (shuttingDown) return;
    if (code !== 0) {
      console.error(`[${name}] exited with ${signal || `code ${code}`}. Stopping AskDB.`);
      shutdown(code ?? 1);
    }
  });

  return child;
}

function shutdown(code = 0) {
  shuttingDown = true;
  for (const child of children) {
    if (!child.killed) {
      child.kill('SIGTERM');
    }
  }
  setTimeout(() => process.exit(code), 500);
}

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));

checkCommand(javaCmd, 'Java runtime');
checkCommand(javacCmd, 'Java compiler');

if (!existsSync(join(frontendDir, 'node_modules'))) {
  console.log('[setup] frontend/node_modules not found. Running npm install in frontend...');
  runBlocking(npmCmd, ['install'], frontendDir);
}

const backendPort = String(await pickBackendPort());

console.log(`[setup] Starting AskDB backend on http://localhost:${backendPort}`);
console.log('[setup] Starting AskDB frontend with Vite');

startProcess(
  'backend',
  process.platform === 'win32' ? 'cmd.exe' : 'bash',
  process.platform === 'win32' ? ['/c', 'run.bat'] : ['run.sh'],
  backendDir,
  { ...process.env, PORT: backendPort }
);

startProcess(
  'frontend',
  npmCmd,
  ['run', 'dev'],
  frontendDir,
  { ...process.env, BACKEND_PORT: backendPort }
);
