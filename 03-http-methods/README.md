# 03 — HTTP Methods (path variables, query params, headers & JSON bodies)

## What this section covers
The HTTP "verbs" (`GET`, `POST`, `PUT`, `DELETE`) and the **four places a request can
carry data**: the URL **path**, the **query string**, request **headers**, and the JSON
**body**. To make it concrete we build a tiny student/course system, and we build it
**twice**: once as a Spring REST API and once as a plain console program — so you can see
that an "endpoint" is really just a function you can call in more than one way.

## What it does exactly
A small system with two roles:

- A built-in **admin** (`admin` / `admin123`) created automatically on first run.
- **Students**, created by the admin.

The admin creates **courses** (each is *active* or *inactive*) and student accounts.
A student logs in, browses courses (optionally filtered to only active ones), enrolls in
active courses, drops them, and lists the courses they hold.

All data is stored in two **plain text** files under `data/` — one record per line, fields
separated by a pipe `|` (`users.txt`, `courses.txt`). You can open them in any editor and
read them without knowing JSON. There is **no database and no service layer** — on purpose.
For the API, one controller (`LearningController`) holds every rule and reads/writes the
files directly.

> **JSON vs. text files — two different ideas.** JSON *does* appear in this project, but
> only in the HTTP request/response **bodies** (that's part of the HTTP lesson). *How we
> save data to disk* is a separate concern, and here we keep it as simple text.

There are **two runnable programs**, and they share the same `data/` files:

1. **API app** (`SpringApiApplication`) — a REST API on `http://localhost:8081`.
   Authentication is per-request via the `X-Username` and `X-Password` **headers**.

   | Method & path | Data it reads | Who | Purpose |
   |---|---|---|---|
   | `GET /api/login` | headers | any | Check your credentials |
   | `GET /api/courses` | headers + `?active=` query param | any | List / filter courses |
   | `GET /api/courses/{id}` | path variable | any | One course |
   | `POST /api/courses` | JSON body | admin | Create a course → `201` |
   | `PUT /api/courses/{id}` | path variable + JSON body | admin | Update a course |
   | `DELETE /api/courses/{id}` | path variable | admin | Delete a course → `204` |
   | `POST /api/users` | JSON body | admin | Create a student → `201` |
   | `GET /api/users` | headers | admin | List users |
   | `POST /api/courses/{courseId}/enroll` | path variable | student | Enroll self |
   | `DELETE /api/courses/{courseId}/enroll` | path variable | student | Drop the course |
   | `GET /api/me/courses` | headers | student | My enrolled courses |

   Failure responses to notice: `401` (missing/bad credentials), `403` (wrong role),
   `404` (no such course), `400` (bad body), `409` (duplicate / inactive course).

2. **Console app** (`console.ConsoleApp`) — the *same* actions through a text menu.
   Log in, then pick numbered options. Each menu item is labelled with the API endpoint
   it mirrors. A course you create here appears in the API, and vice-versa.

## Key concepts / new things introduced
- **`@RestController` + `@RequestMapping("/api")`** — `LearningController.java`, the single
  class that is the whole student/course API.
- **`@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping`** — one per HTTP verb;
  see the course endpoints.
- **`@PathVariable`** — id taken from the URL, e.g. `GET /api/courses/{id}`.
- **`@RequestParam`** — the optional `?active=true|false` filter in `listCourses`.
- **`@RequestHeader`** — `X-Username` / `X-Password` used for auth on every endpoint.
- **`@RequestBody`** — JSON deserialized into a Java **record** (`CourseRequest`, `UserRequest`).
- **Response DTO** — `UserView` returns users *without* their password.
- **HTTP status codes** — `@ResponseStatus` for `201`/`204`, `ResponseStatusException` for errors.
- **Shared file persistence** — `store/FileStore.java`, used by both programs.

## Bonus controller — body & content-type formats (`BodyExampleController`)
A second controller under `/examples` that focuses on the request/response **body** and how
Spring converts it to/from different formats. The key idea: **you write the Java object
once, and Spring serialises it to whatever the caller asks for.** Two headers drive it —
`Content-Type` (what you send) and `Accept` (what you want back) — mapped to the `consumes`
and `produces` attributes on each mapping.

| Endpoint | Consumes → Produces | Teaches |
|---|---|---|
| `POST /examples/echo-text` | text/plain → text/plain | raw `String` body |
| `POST /examples/json` | JSON → JSON | object ⇄ JSON |
| `POST /examples/xml` | XML → XML | object ⇄ XML (same code shape as JSON) |
| `GET /examples/greeting` | — → JSON **or** XML | **content negotiation** via `Accept` |
| `POST /examples/form` | form-urlencoded | HTML form fields via `@RequestParam` |
| `POST /examples/flexible` | JSON *or* text | same path, method picked by `Content-Type` |
| `GET /examples/download` | — → octet-stream | binary/file download |
| `POST /examples/upload` | multipart/form-data | file upload (`MultipartFile`) |
| `POST /examples/echo` | anything | inspect the raw body + `Content-Type` |
| `POST /examples/tickets` | JSON → JSON/XML | real-world create: `201` + `Location` header |

Status codes to notice here: wrong `Content-Type` → **415 Unsupported Media Type**;
asking (via `Accept`) for a format the endpoint can't produce → **406 Not Acceptable**.

XML support comes from the `jackson-dataformat-xml` dependency in `pom.xml` — just having it
on the classpath is enough for Spring to auto-register the XML converter.

Ready-made requests are in **`body-examples.http`** (run the same `/examples/greeting`
request with `Accept: application/json` then `Accept: application/xml` to see one endpoint
answer in two formats).

## Web client (browser front-end)
A tiny **HTML + CSS + vanilla JavaScript** UI that consumes the API — no framework, no build
step. It lives in `src/main/resources/static/` (`index.html`, `styles.css`, `app.js`) and is
**served by Spring itself**, so once the API is running just open:

```
http://localhost:8081/
```

Because the page is served from the same origin as the API, there is **no CORS setup** to
worry about. Log in as `admin` / `admin123` to get the admin screen (create courses &
students, activate/deactivate, rename, delete, filter by active) or as a student you created
to get the student screen (browse/filter courses, enroll, drop, see "my courses").

The whole point for the interns: open the browser dev-tools **Network** tab and watch each
click become an HTTP request — the `X-Username`/`X-Password` headers, the `?active=` query
param, the JSON bodies, and the `GET`/`POST`/`PUT`/`DELETE` methods are all visible there.
`app.js` sends every request through one small `api()` function so it's easy to read.

**Which URL to open:**
- **Recommended — served by Spring:** `http://localhost:8081/`. Same origin as the API, so
  no CORS is involved at all.
- **IntelliJ's built-in preview** (right-click `index.html` → *Open in Browser*, a URL like
  `http://localhost:63343/...`) also works: the front-end detects it isn't on port 8081 and
  calls `http://localhost:8081` directly, and the backend's `WebConfig` allows that
  cross-origin call via CORS. (Don't use a `file://` page — there's no server to talk to.)

> If login 404s, you're almost certainly hitting a server that isn't the Spring app (check
> the port). Make sure `SpringApiApplication` is running on 8081.

## How to run
Everything happens **inside this folder** (`03-http-methods/`). Requires JDK 25.

### Run the API (Spring) + the web client
- **IntelliJ:** open `SpringApiApplication` and click ▶, then open `http://localhost:8081/`
  in a browser for the UI. You can also open `requests.http` / `body-examples.http` and run
  the requests, or use curl below.
- **Command line (Maven):**
  ```bash
  cd 03-http-methods
  mvn spring-boot:run
  ```

Exercise it with curl (built-in admin `admin`/`admin123`):
```bash
# Log in (headers)
curl -i http://localhost:8081/api/login \
  -H "X-Username: admin" -H "X-Password: admin123"

# Create a course (POST + JSON body)
curl -i -X POST http://localhost:8081/api/courses \
  -H "X-Username: admin" -H "X-Password: admin123" \
  -H "Content-Type: application/json" \
  -d '{"name":"Intro to Spring","active":true}'

# List only active courses (query param)
curl -s "http://localhost:8081/api/courses?active=true" \
  -H "X-Username: admin" -H "X-Password: admin123"

# Get one course by id (path variable)
curl -s http://localhost:8081/api/courses/1 \
  -H "X-Username: admin" -H "X-Password: admin123"

# Create a student, then enroll as that student
curl -i -X POST http://localhost:8081/api/users \
  -H "X-Username: admin" -H "X-Password: admin123" \
  -H "Content-Type: application/json" \
  -d '{"username":"amir","password":"secret"}'

curl -i -X POST http://localhost:8081/api/courses/1/enroll \
  -H "X-Username: amir" -H "X-Password: secret"

curl -s http://localhost:8081/api/me/courses \
  -H "X-Username: amir" -H "X-Password: secret"

# One endpoint, two formats (content negotiation)
curl -s http://localhost:8081/examples/greeting?name=Sara -H "Accept: application/json"
curl -s http://localhost:8081/examples/greeting?name=Sara -H "Accept: application/xml"
```

### Run the console app
- **IntelliJ:** open `console/ConsoleApp` and click ▶, then follow the menu.
- **Command line** (after `mvn compile`):
  ```bash
  cd 03-http-methods
  mvn -q compile
  java -cp target/classes com.practice._03httpmethods.console.ConsoleApp
  ```
  (The console app uses no third-party libraries — just plain file IO — so once compiled it
  runs with a bare classpath. IntelliJ's ▶ works too.)

Try this to see the two apps share state: create a course in the console, then hit
`GET /api/courses` in the API — it's there.

## Notes / gotchas
- **Package name:** the course convention is `com.practice.<nn><topic>`, which here would be
  `com.practice.03httpmethods` — but a Java package **cannot start with a digit**. So the
  package is `com.practice._03httpmethods` (leading underscore).
- **`data/` is generated,** not source. It's git-ignored. Delete the folder to reset to a
  fresh state (just the admin account). The admin is re-seeded automatically.
- **Passwords are stored in plain text.** Fine for learning, never in production.
- **Headers vs. real auth:** sending the password on every request in a custom header is a
  teaching shortcut. Real systems use hashed passwords + tokens/sessions.
- **Don't run both apps writing at the same time** — the file store isn't built for
  concurrent writers from two processes; it's kept intentionally simple.
- **`GET` never changes data.** Notice enroll/drop are `POST`/`DELETE`, not `GET` — that
  distinction is the whole point of the section.
