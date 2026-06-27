# AskDB Frontend

React + Vite dashboard for AskDB.

## Recommended dev command

Run the full app from the root `AskDB` folder:

```bash
npm run dev
```

That starts both the backend and the frontend together.

## Frontend-only commands

```bash
npm install
npm run dev
npm run build
npm run preview
```

The frontend runs at `http://localhost:5173` and proxies `/api` requests to the backend. By default the backend proxy points to `http://localhost:9090`.

To use another backend port through the root dev script:

```bash
BACKEND_PORT=9090 npm run dev
```
