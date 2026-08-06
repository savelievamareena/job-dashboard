# Job dashboard

A board over the job postings that were already parsed and selected for application. Spring Boot
serves the data, React renders it. Nothing here searches LinkedIn or spends API credits: it only
shows what the parsing produced, and stores how far each application got.

The board holds **selected postings only**. A posting that did not make it into `selected.csv` is
not shown at all.

## Stack

| Part     | Built with                                      |
|----------|-------------------------------------------------|
| Backend  | Java 21, Spring Boot 3.4, Maven, Commons CSV    |
| Frontend | React 19, TypeScript, Vite                      |
| Storage  | the CSV and JSON files the parsing already wrote |

## Setup, once

Tell it where the parsed vacancies live:

```bash
cp .env.example .env      # then set DASHBOARD_ROOT to the folder holding DailySearch
```

There is no default on purpose. A personal path does not belong in a repository, and a wrong one
would show an empty board instead of failing, which reads as "no vacancies today" rather than
"misconfigured". Without it the app refuses to start and says what to set. A real `DASHBOARD_ROOT`
environment variable overrides the file.

## Running it

```bash
cd backend && mvn spring-boot:run
```

Then open http://localhost:8765. That one command builds the React app and lets Spring Boot serve
it next to the API on the same port.

`JAVA_HOME` has to point at a JDK 21. On macOS this is worth checking first, because Homebrew
keeps `openjdk@21` keg-only and `/usr/bin/java` is a stub that fails, so Maven otherwise dies with
"Unable to locate a Java Runtime":

```bash
java -version                      # expect 21.x
export JAVA_HOME=$(brew --prefix openjdk@21)   # if it is not set
```

## Working on the frontend

For hot reload, run the Vite dev server against the backend. It serves the UI on 5173 and proxies
`/api` to 8765, so the backend skips the frontend build:

```bash
cd backend && mvn spring-boot:run -DskipFrontend
```

```bash
cd frontend && npm install && npm run dev
```

Then open http://localhost:5173.

## Where the data comes from

`DASHBOARD_ROOT` from `.env` points at the folder the parsing writes into:

```
<root>/<date>/_descriptions/<jobId>.json   cached posting record
<root>/<date>/_descriptions/<jobId>.txt    cached description text
<root>/<date>/_titles.json                 jobId to title, older days only
<root>/<date>/<track>/jobs.csv             everything parsed that day
<root>/<date>/<track>/selected.csv         the ones worth applying to: what the board shows
```

`selected.csv` drives the scan. It already carries company, title and gap, so a selected posting
still shows up if `jobs.csv` was rewritten underneath it. The cached record fills in level, job
type, location, applicant count and the Easy Apply flag where it exists.

Two record shapes appear in the cache, from different parsing runs: the newer one uses `location`,
`applicants` and `easy_apply`, the older one `job_location` and `applies` with no flag at all. Both
are read. A missing Easy Apply flag shows as `?`, which is not the same as "external site".

The board reads these files on every request, so a fresh parsing run shows up on reload. Nothing
is copied or imported, and no vacancy data lives in this repository.

## What it writes

One file, `DailySearch/_status.json`, mapping a posting URL to its status and note:

```json
{
  "https://www.linkedin.com/jobs/view/123": { "status": "applied", "note": "sent on Thursday" }
}
```

Statuses are `reviewing`, `applied` and `not a fit`; clearing both fields removes the entry. Writes
go through a temp file and an atomic move, so an interrupted save cannot leave half a map behind.
Everything else on disk is read only.

## API

| Method | Path                    | Does                                              |
|--------|-------------------------|---------------------------------------------------|
| GET    | `/api/vacancies`        | the selected postings plus the available statuses |
| PUT    | `/api/vacancies/status` | stores `{ url, status, note }`                    |

## Tests

```bash
cd backend && mvn test
```

They cover the file reader against a temp folder (only selected rows returned, both cached record
shapes, the title fallback chain, newest day first, missing folders), the status store (round trip,
one entry not clobbering another, empty entries removed, a corrupt file not taking the app down),
and the `.env` loader (parsing, lookup in a parent directory, not overriding a real environment
variable, and the refusal to start on an unresolved `DASHBOARD_ROOT`).

## History

Ported from a single file stdlib Python app that lived at `CVs/Job_Search/app.py`, removed once
this one ran from a single command. The status file format is unchanged, so nothing marked in the
old dashboard was lost.
