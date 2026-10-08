# SmartGroup Team Analyzer

SmartGroup is a team and project management app with an HTML/CSS/JavaScript frontend, a Spring Boot API, and a MySQL database.

## GitHub Pages frontend

The workflow in `.github/workflows/pages.yml` publishes the static frontend from `backend/src/main/resources/static` whenever you push to `main`.

1. Push this project to a GitHub repository.
2. In the repository, open **Settings → Pages** and set **Build and deployment → Source** to **GitHub Actions**.
3. Push to `main` or run **Actions → Deploy SmartGroup frontend to GitHub Pages → Run workflow**.
4. When the workflow completes, open **Settings → Pages** for the site URL.

GitHub Pages hosts only the static frontend. Sign-in, registration, email verification, shared data, and MySQL require the Spring Boot backend and a reachable MySQL database deployed separately.

After you deploy the backend, set `window.SMARTGROUP_API_URL` in `backend/src/main/resources/static/api-config.js` to the backend's public HTTPS URL, then push the change. The backend must allow requests from your GitHub Pages origin (CORS) and be configured for cross-site authentication if it uses cookies.

## Spring Boot backend

Use Java 17 and Maven. Start the backend from the `backend` folder after setting these environment variables in your hosting provider:

- `DB_URL` — JDBC URL for the hosted MySQL database
- `DB_USER` and `DB_PASSWORD` — database credentials
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` — SMTP configuration for verification and welcome email

The app intentionally has no database username or password fallback in `application.properties`. Add real values only to your hosting provider's secret/environment settings; never commit them to Git.

The backend's `schema.sql` initializes the database tables on startup. Configure the database provider to allow connections from the backend host.

## Local run

From `backend`, set the variables above for your own MySQL and SMTP accounts, then run:

```sh
mvn spring-boot:run
```

Open `http://localhost:8080`. Keep credentials in environment variables or an ignored local `.env` file; do not put real credentials in this repository.
