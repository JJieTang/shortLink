# Frontend Workspace

This workspace contains the React SPA for ShortLink.

Current status:

- Vite + React + TypeScript app shell is set up
- Tailwind CSS is configured
- Shared API client and auth session helpers are available
- Auth, links, history, and analytics flows are wired into the app shell

Install and run locally:

```bash
npm install
npm run dev
```

Recommended local dev setup:

1. Copy `.env.example` to `.env`.
2. Leave `VITE_API_BASE_URL` empty so the browser uses same-origin `/api` requests.
3. Let Vite proxy `/api` requests to `VITE_DEV_PROXY_TARGET` (defaults to `http://localhost:8080`).

This keeps local API traffic on the frontend origin while still forwarding requests to the Spring Boot backend.
