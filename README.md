# SmartGroup Team Analyzer

A team planning and analysis project with a browser frontend and a Spring Boot/MySQL API.

## Files included

- `frontend/index.html` and `frontend/styles.css`: supplied browser UI files.
- `backend/`: supplied Java API source, Maven configuration, MySQL schema, and environment-based connection settings.

**The uploaded project is incomplete:** `index.html` loads `frontend/app.js`, but that file was not among the supplied files. The original README describes browser functionality implemented by that script. Add the original `app.js` before expecting the browser interface to work. No replacement script has been generated here, so application behavior and source are not fabricated.

The uploaded `face_attendance.zip` is a separate Python project and is not included in this SmartGroup repository.

## Backend setup

Requirements: Java 17+, Maven, and a running MySQL server. Create a database or allow the configured URL to create `smartgroup` on startup. Configure credentials in your shell; do not commit local passwords:

PowerShell:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/smartgroup?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USER = "root"
$env:DB_PASSWORD = "your-local-mysql-password"
cd backend
mvn spring-boot:run
```

The schema is initialized from `backend/src/main/resources/schema.sql`. `DB_PASSWORD` defaults to an empty value; set it in your environment for a protected MySQL account.

## Frontend

After restoring the missing `frontend/app.js`, open `frontend/index.html` in a browser. The supplied UI links to the API at its original behavior/configuration; check the restored script for its API URL and any required local serving steps.

## GitHub

Create an empty repository on GitHub (without initializing it with a README, license, or `.gitignore`), then run these commands from this folder. Replace the remote URL with your repository's HTTPS or SSH URL:

```bash
git init
git add .
git status
git commit -m "Prepare SmartGroup Team Analyzer"
git branch -M main
git remote add origin https://github.com/USERNAME/smartgroup-team-analyzer.git
git push -u origin main
```

Review `git status` before committing. `.gitignore` excludes build output, environment files, and common local secrets. Never put a real database password, API key, or private key in tracked files.

