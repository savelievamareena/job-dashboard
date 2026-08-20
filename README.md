# Job dashboard

A board over the job postings that were already parsed and selected for application. Spring Boot
serves the data, React renders it. Nothing here searches LinkedIn or spends API credits: it only
shows what the parsing produced, and stores how far each application got.

The board holds **selected postings only**. A posting that did not make it into `selected.csv` is
not shown at all.

## Stack

| Part     | Built with                                   |
|----------|----------------------------------------------|
| Backend  | Java 21, Spring Boot 3.4, Maven, plain JDBC  |
| Frontend | React 19, TypeScript, Vite                   |
| Storage  | Postgres 18, loaded from the parsing output  |

## Setup, once

Point `.env` at the folder the parsing writes into, and set a database password:

```bash
cp .env.example .env      # then set DASHBOARD_ROOT and DB_PASSWORD
```

`DASHBOARD_ROOT` has no default on purpose: a personal path does not belong in a repository. Only
the loader reads it; the dashboard itself never opens those folders.

Then start the database, create the schema, and load the parsing output into it:

```bash
docker compose up -d
docker compose exec -T db psql -U postgres -d jobdashboard -v ON_ERROR_STOP=1 < db/reset-schema.sql
python3 db/migrate.py | docker compose exec -T db psql -U postgres -d jobdashboard -v ON_ERROR_STOP=1
```

`db/reset-schema.sql` starts by dropping `vacancy`, so it is for an empty database only; it refuses
to run otherwise. To change the shape of one already in use, write an alter script beside it;
`db/alter-2026-08-11-board-on-db.sql` is the worked example.

Both `db` and the backend bind to `127.0.0.1` only (see `docker-compose.yml` and
`application.yml`), and the API has no authentication in front of it. That is fine for a personal
tool running on one machine; it is not something to expose to a LAN or the internet without adding
auth and TLS first.

Re-run the loader after every parsing run. It is idempotent for `vacancy` and `job_status`:
postings are merged by id, `is_selected` is only ever raised, and `job_status` is never touched.
`cv_queue` is the exception — it is truncated and reloaded from `review-queue.csv` on every run,
since that queue has no marks of its own to lose.

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

The dashboard reads one database. `db/migrate.py` is what puts the parsing output into it, walking
the folders `DASHBOARD_ROOT` points at:

```
<root>/<date>/_descriptions/<jobId>.json   cached posting record
<root>/<date>/_descriptions/<jobId>.txt    cached description text
<root>/<date>/_titles.json                 jobId to title, older days only
<root>/<date>/<track>/jobs.csv             everything parsed that day
<root>/<date>/<track>/selected.csv         the ones worth applying to: what the board shows
```

Two record shapes appear in the cache, from different parsing runs: the newer one uses `location`,
`applicants` and `easy_apply`, the older one `job_location` and `applies` with no flag at all. Both
are read. A missing Easy Apply flag shows as `?`, which is not the same as "external site". The
`applies` key is ignored where it is zero: it is a default the newer parsing writes on everything,
never a count, and reading it literally overwrote real applicant numbers with a nought.

One row in the database is one posting, not one sighting of it. A posting found again on a later
day updates its row, so the board shows it once however many days it kept turning up. Both the
board and the trend charts date a posting by when it went up before anything else:
`posted_at`, which only reaches back to 2026-08-09. Past that they diverge one step: the charts
fall straight back to `found_date`, the day it was first seen; the board takes one more step
first, `selected_date`, the day it was picked - a posting picked before `posted_at` existed should
still show the day she chose it, not the day some later scan happened to notice it again.
`selected_date` was dropped once on 2026-08-15 for reading no purpose once the board stopped
showing it first, then brought back the same day, narrower, once that turned out to be the wrong
call for the postings `posted_at` cannot reach: a stopgap, gone again once every picked posting
carries its own `posted_at`.

The board is only as fresh as the last loader run. That is the trade for a page that never opens a
file or spends an API credit while rendering.

Before August 2026 the board read the folders directly on every request and only the statistics
page used the database. Both read it now, so the two can no longer disagree about what was found.

### Which CV to send

The `CV` column answers what goes out for that company. It comes from the queue the tailoring
appends to, one per core, which the loader imports into `cv_queue`:

```
<root>/cv-tailored/Poland/<core>/review-queue.csv     company,url,pdf_path,built,verdict
```

The list of cores is configured in `application.yml` and must stay there. Reading it back out of
`cv_queue` instead would drop any core with nothing built yet, and that core still owns its track:
lose `Fullstack` and every fullstack posting starts being answered from the Frontend queue.

A row whose `pdf_path` sits in a folder named after the company is a CV tailored for it, and shows
as **tailored**; any other path under the core is the core CV sent unchanged, and shows as
**core CV**. A company with no row yet reads **not built**, which is not the same as either answer.

A track is answered from the core of the same name: a frontend posting is told about the Frontend
queue only. The two cores are deliberately different documents, down to the job titles and the
2022-2024 employer name, so a fullstack CV is no answer for a frontend posting. The tracks that
name no core, `other-stacks` and `unsorted`, take whichever core knows the company.

A row is matched to a posting by `url`, so two ads from the same agency answer separately: one can
read **tailored** while the other reads **core CV**. Rows written before the queue carried a `url`
are matched by company and answer for every posting of it, which is all they can honestly say.

## What it writes

Mostly `job_status`: a posting id, a status and a note. Statuses are `applied`, `not a fit` and
`closed`; clearing both fields deletes the row, the same way an untouched posting has none.

That table is the loader's one blind spot: a refill from a file would throw away every mark made
since the last import, which is why `db/migrate.py` has no insert into it at all.

The board can also set `vacancy.apply_url`, the address behind the Apply button, pasted by hand
through `PUT /api/vacancies/apply-url`. Unlike `job_status` this column is also written by the
loader, from the paid lookup that used to fill it (see `db/alter-2026-08-12-apply-url.sql`) — but
the merge is `coalesce(new, existing)`, so a re-scrape with nothing in that field never blanks a
value the board pasted by hand; it only gets overwritten by a real value from the same lookup,
which stopped answering on 2026-08-13.

And `vacancy.may_submit`, through `PUT /api/vacancies/may-submit`: a checkbox she sets by hand to
tell the apply agent it does not have to wait for her review on this posting before submitting.
`db/migrate.py` never touches this column, so a re-import never resets it (see
`db/alter-2026-08-20-may-submit.sql`).

Marks used to live in `DailySearch/_status.json` and were mirrored into the table on every load, so
the same mark existed twice and the file was the one that won. The file is no longer read or
written. Everything that was in it was carried into the table by the alter script.

## When the database is down

Both pages answer `503` with the text `база недоступна: проверьте, что контейнер запущен`. The
application still starts without a database on purpose: one that refuses to boot cannot say why.
Nothing needs restarting once the container is back.

## API

| Method | Path                        | Does                                                        |
|--------|------------------------------|--------------------------------------------------------------|
| GET    | `/api/vacancies`             | the selected postings plus the available statuses            |
| PUT    | `/api/vacancies/status`      | stores `{ url, status, note }`                                |
| PUT    | `/api/vacancies/apply-url`   | stores `{ url, applyUrl }`; blank clears it, see above        |
| PUT    | `/api/vacancies/may-submit`  | stores `{ url, maySubmit }`, see above                        |
| GET    | `/api/statistics`            | the three trend views plus `scanDays`, for the charts page    |

## Tests

```bash
cd backend && mvn test
```

They cover the CV rules (two postings of one company answered separately, a row without a url
answering company wide, a row naming the posting beating the company-wide one, a track answered
from its own core only, a track without a core falling back, company matched regardless of case,
an empty queue) and the `.env` loader (parsing, lookup in a parent directory, not overriding a
real environment variable).

The readers themselves are not covered, and were not replaced with database tests: that would mean
a test container, and these three repositories are between four and twenty lines of SQL each. They
were checked instead by rebuilding the API response from the database and comparing it field by
field with the file-backed one it replaced, 78 postings across 17 fields.

## History

Ported from a single file stdlib Python app that lived at `CVs/Job_Search/app.py`, removed once
this one ran from a single command. It kept that app's status file byte for byte, so nothing
marked in the old dashboard was lost; in August 2026 the marks moved into `job_status` and the
file was retired, again without losing any.
