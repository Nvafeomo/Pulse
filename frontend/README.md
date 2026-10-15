# Pulse Dashboard

Angular dashboard for the Pulse notification API. Enter an API key, send
notifications over email or SMS, browse your jobs with status filters and
pagination, and watch a job move from `PROCESSING` to `DELIVERED`.

## Run locally

Start the API first (see the root README), then:

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200 and paste your API key into the header
(the local dev key is `pulse-dev-key-local`).

The API URL lives in `src/app/core/config.ts`, and the API only accepts
browser requests from origins listed in `pulse.cors.allowed-origins`.

## Build

```bash
npm run build
```

Output goes to `dist/frontend/browser`.
