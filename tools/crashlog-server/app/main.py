"""Crash and diagnostics sink for Totum.

Design rules, in priority order:

1. **Never lose a report.** The raw body is written to disk *before* anything is
   parsed or indexed. A malformed, truncated or unexpected payload is still stored
   and still visible — a crash report you dropped because it didn't validate is
   worse than useless.
2. **Readable without tooling.** Reports land as plain files under a bind-mounted
   directory, so they can be read over SSH with `cat` even if this service is down.
3. **Searchable.** Key fields are mirrored into SQLite so "every crash on commit
   abc123" or "group by exception" is one query, which is how you actually find a
   pattern rather than eyeballing files.
"""

from __future__ import annotations

import json
import os
import sqlite3
import uuid
from contextlib import closing
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Query, Request
from fastapi.responses import HTMLResponse, JSONResponse, PlainTextResponse
from fastapi.templating import Jinja2Templates

DATA_DIR = Path(os.environ.get("CRASHLOG_DATA", "/data"))
REPORTS_DIR = DATA_DIR / "reports"
DB_PATH = DATA_DIR / "index.db"

# Generous: a verbose report with a few hundred events is still tens of KB, and the
# instruction is to prioritise collecting data. This only stops a runaway upload.
MAX_BODY_BYTES = 8 * 1024 * 1024

# Oldest reports are pruned past this so the Pi's disk can't fill.
MAX_TOTAL_MB = int(os.environ.get("CRASHLOG_MAX_TOTAL_MB", "512"))

TEMPLATES = Jinja2Templates(directory=str(Path(__file__).parent / "templates"))

app = FastAPI(title="Totum crash log", docs_url="/api/docs", redoc_url=None)


def _connect() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def _init_storage() -> None:
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    with closing(_connect()) as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS reports (
                id             TEXT PRIMARY KEY,
                received_at    TEXT NOT NULL,
                path           TEXT NOT NULL,
                kind           TEXT,
                app_version    TEXT,
                git_commit     TEXT,
                exception      TEXT,
                message        TEXT,
                device         TEXT,
                android        TEXT,
                install_id     TEXT,
                event_count    INTEGER,
                bytes          INTEGER,
                parsed         INTEGER NOT NULL DEFAULT 1
            )
            """
        )
        connection.execute("CREATE INDEX IF NOT EXISTS reports_received ON reports(received_at DESC)")
        connection.execute("CREATE INDEX IF NOT EXISTS reports_commit ON reports(git_commit)")
        connection.execute("CREATE INDEX IF NOT EXISTS reports_exception ON reports(exception)")
        connection.commit()


_init_storage()


def _dig(payload: Any, *keys: str) -> str | None:
    """First present, non-empty value among [keys], searched case-insensitively.

    Deliberately forgiving about the payload's shape: the app's report format will
    change, and a field rename should degrade to "unknown" rather than 500.
    """
    if not isinstance(payload, dict):
        return None
    lowered = {str(k).lower(): v for k, v in payload.items()}
    for key in keys:
        value = lowered.get(key.lower())
        if isinstance(value, (str, int, float)) and str(value).strip():
            return str(value)
    return None


def _prune() -> None:
    files = sorted(REPORTS_DIR.rglob("*.json"), key=lambda p: p.stat().st_mtime)
    total = sum(f.stat().st_size for f in files)
    limit = MAX_TOTAL_MB * 1024 * 1024
    while files and total > limit:
        oldest = files.pop(0)
        total -= oldest.stat().st_size
        oldest.unlink(missing_ok=True)
        with closing(_connect()) as connection:
            connection.execute("DELETE FROM reports WHERE path = ?", (str(oldest),))
            connection.commit()


@app.post("/ingest")
async def ingest(request: Request) -> JSONResponse:
    """Accept a report. Stores first, parses second — a bad payload is still kept."""
    body = await request.body()
    if len(body) > MAX_BODY_BYTES:
        body = body[:MAX_BODY_BYTES]

    now = datetime.now(timezone.utc)
    report_id = f"{now.strftime('%Y%m%dT%H%M%S')}-{uuid.uuid4().hex[:8]}"
    day_dir = REPORTS_DIR / now.strftime("%Y-%m-%d")
    day_dir.mkdir(parents=True, exist_ok=True)
    path = day_dir / f"{report_id}.json"
    path.write_bytes(body)

    parsed: Any = None
    try:
        parsed = json.loads(body.decode("utf-8", errors="replace"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        parsed = None

    events = parsed.get("events") if isinstance(parsed, dict) else None
    with closing(_connect()) as connection:
        connection.execute(
            """
            INSERT INTO reports (id, received_at, path, kind, app_version, git_commit,
                                 exception, message, device, android, install_id,
                                 event_count, bytes, parsed)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                report_id,
                now.isoformat(),
                str(path),
                _dig(parsed, "kind", "reportKind") or "unknown",
                _dig(parsed, "appVersion", "versionName", "version"),
                _dig(parsed, "gitCommit", "commit", "buildCommit"),
                _dig(parsed, "exception", "exceptionClass", "throwable"),
                _dig(parsed, "message", "exceptionMessage"),
                _dig(parsed, "device", "model", "deviceModel"),
                _dig(parsed, "android", "androidVersion", "sdk"),
                _dig(parsed, "installId", "installationId"),
                len(events) if isinstance(events, list) else None,
                len(body),
                1 if parsed is not None else 0,
            ),
        )
        connection.commit()
    _prune()
    # The app must never block or retry on our response body; a bare id is plenty.
    return JSONResponse({"id": report_id}, status_code=202)


@app.get("/healthz")
def healthz() -> PlainTextResponse:
    return PlainTextResponse("ok")


@app.get("/", response_class=HTMLResponse)
def index(
    request: Request,
    commit: str | None = None,
    exception: str | None = None,
    limit: int = Query(default=100, le=1000),
) -> HTMLResponse:
    clauses, params = [], []
    if commit:
        clauses.append("git_commit LIKE ?")
        params.append(f"{commit}%")
    if exception:
        clauses.append("exception LIKE ?")
        params.append(f"%{exception}%")
    where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    with closing(_connect()) as connection:
        rows = connection.execute(
            f"SELECT * FROM reports {where} ORDER BY received_at DESC LIMIT ?",
            (*params, limit),
        ).fetchall()
        totals = connection.execute(
            "SELECT exception, COUNT(*) AS n FROM reports GROUP BY exception ORDER BY n DESC LIMIT 10"
        ).fetchall()
    return TEMPLATES.TemplateResponse(
        request=request,
        name="index.html",
        context={"rows": rows, "totals": totals, "commit": commit or "", "exception": exception or ""},
    )


@app.get("/report/{report_id}", response_class=HTMLResponse)
def report(request: Request, report_id: str) -> HTMLResponse:
    with closing(_connect()) as connection:
        row = connection.execute("SELECT * FROM reports WHERE id = ?", (report_id,)).fetchone()
    if row is None:
        return HTMLResponse("<h1>No such report</h1>", status_code=404)
    raw = Path(row["path"]).read_text(encoding="utf-8", errors="replace")
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        payload = None
    return TEMPLATES.TemplateResponse(
        request=request,
        name="report.html",
        context={"row": row, "payload": payload, "raw": raw},
    )


@app.get("/report/{report_id}/raw")
def report_raw(report_id: str) -> PlainTextResponse:
    with closing(_connect()) as connection:
        row = connection.execute("SELECT path FROM reports WHERE id = ?", (report_id,)).fetchone()
    if row is None:
        return PlainTextResponse("not found", status_code=404)
    return PlainTextResponse(Path(row["path"]).read_text(encoding="utf-8", errors="replace"))


@app.get("/api/reports")
def api_reports(limit: int = Query(default=50, le=1000)) -> JSONResponse:
    """Machine-readable list — how Claude triages without opening a browser."""
    with closing(_connect()) as connection:
        rows = connection.execute(
            "SELECT * FROM reports ORDER BY received_at DESC LIMIT ?", (limit,)
        ).fetchall()
    return JSONResponse([dict(r) for r in rows])


@app.get("/latest", response_class=PlainTextResponse)
def latest() -> PlainTextResponse:
    """The newest report as plain text — the one-command triage over SSH or curl."""
    with closing(_connect()) as connection:
        row = connection.execute("SELECT * FROM reports ORDER BY received_at DESC LIMIT 1").fetchone()
    if row is None:
        return PlainTextResponse("no reports yet")
    raw = Path(row["path"]).read_text(encoding="utf-8", errors="replace")
    try:
        pretty = json.dumps(json.loads(raw), indent=2)
    except json.JSONDecodeError:
        pretty = raw
    header = f"# {row['id']}  {row['received_at']}  {row['app_version']} @ {row['git_commit']}\n\n"
    return PlainTextResponse(header + pretty)
