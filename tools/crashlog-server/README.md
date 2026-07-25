# UniApp crash / diagnostics sink

Receives crash and diagnostics reports from the app and makes them readable. Runs as a
container on the Pi, reached through the existing nginx at
`https://crashlog.333133333.xyz`.

## Design rules, in priority order

1. **Never lose a report.** The raw body is written to disk *before* parsing or
   indexing. A malformed or truncated payload is still stored and still listed (flagged
   `unparsed`) — a report dropped for failing validation is worse than useless.
2. **Readable without tooling.** Reports are plain files under `/home/pi/crashlog-data`,
   so `cat` over SSH works even if this service is down.
3. **Searchable.** Key fields are mirrored into SQLite, so "every crash on commit
   abc123" or "group by exception" is one query.

## Endpoints

| Route | Purpose |
|---|---|
| `POST /ingest` | What the app posts to. **Unauthenticated** (see below). Returns `202` with an id |
| `GET /` | Browsable list, filterable by commit and exception |
| `GET /report/{id}` | One report: message, stack trace, and the **event trail** rendered as a timeline |
| `GET /report/{id}/raw` | The raw JSON as received |
| `GET /latest` | Newest report as plain text — the one-command triage |
| `GET /api/reports` | JSON list, for triaging without a browser |
| `GET /healthz` | Liveness |

## Why `/ingest` is unauthenticated

The app has to post without a human present, so it can't use the Google login that
gates everything else. A shared secret would have to ship inside the APK — and this
repo is public, so it would be extractable and therefore not a secret. The exposure is
bounded instead: nginx caps the body size and rate-limits the path, and the worst case
is junk reports, which are cheap to delete. **Everything except `/ingest` is behind
Google login.**

## Deploying / updating on the Pi

```bash
cd ~/code/dewiuniapp && git pull
cd tools/crashlog-server && docker compose up -d --build
```

Its own compose project on purpose: bringing the sink up or down must never restart
nginx, Jellyfin or the VPN containers. nginx reaches it via `host.docker.internal:9140`,
the same way `graidentestdev` already does.

## Reading reports

Browse (Google login): <https://crashlog.333133333.xyz>

From a shell — no host port is published, so go via the files or the container:

```bash
# newest report, plain cat (works even if the container is down)
ssh pi@333133333.xyz 'cat "$(ls -t /home/pi/crashlog-data/reports/*/*.json | head -1)"'

# list recent
ssh pi@333133333.xyz 'ls -t /home/pi/crashlog-data/reports/*/*.json | head'

# the service's own pretty view / API
ssh pi@333133333.xyz 'docker exec uniapp-crashlog python -c "import urllib.request;print(urllib.request.urlopen(\"http://localhost:9140/latest\").read().decode())"'
```

## Pi deployment notes (paid for once, worth keeping)

- **nginx config lives in two places.** The tracked source is
  `dot-files/serverconfig/code/server_docker/data/nginx/app.conf`; `generate.sh` copies
  **tracked files only** into a root-owned `bin/`, and that copy is what nginx mounts.
  Editing only the source changes nothing live.
- **Bind-mounted *files* pin an inode.** nginx had been started against an older inode of
  `app.conf`, so appending to the host file was invisible inside the container (different
  inode *and* size). Fix without downtime: write through the container
  (`docker exec -i nginx_dewi sh -c 'cat > /etc/nginx/conf.d/default.conf'`) as well as
  the tracked source, then `nginx -s reload`.
- **`host.docker.internal` cannot reach a loopback-published port.** It resolves to the
  bridge gateway (172.17.0.1); a container published on `127.0.0.1:9140` is unreachable
  from there — it 504s. This service joins nginx's own `bin_private` network instead and
  is proxied by container name, so no host port is published at all.
- Reload, never restart: `docker exec nginx_dewi nginx -t && docker exec nginx_dewi nginx -s reload`
  leaves Jellyfin, the VPN containers and nginx's uptime untouched.
