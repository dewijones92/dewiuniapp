---
title: UniApp docs
kind: index
updated: 2026-07-24
---

# UniApp documentation

Living documentation for UniApp — the single Android app replacing PipePipe
(YouTube) and AntennaPod (podcasts) with one unified domain model. `CLAUDE.md`
(repo root) holds the binding decisions and quality bar; these docs track the
detail and keep it current.

## Layout

| Dir | What | Convention |
|---|---|---|
| [`architecture.md`](architecture.md) | The unified seams + module map | one file |
| [`features/`](features/_index.md) | One doc per feature — status, seam, files, tests | `<feature>.md` + `_index.md` |
| [`todos/`](todos/_index.md) | The live backlog — one file per item | `<slug>.md` + `_index.md` |
| [`tests/`](tests/_index.md) | Testing strategy + coverage map | one file |

## Frontmatter

Every doc starts with YAML frontmatter. Common fields:

```yaml
---
title: Search history
kind: feature | todo | index | reference
status: shipped | in-progress | planned | dropped   # features & todos
priority: high | medium | low                        # todos
area: search | playback | channel | downloads | …
updated: 2026-07-24                                  # bump when you edit
---
```

## Maintenance (the one rule)

A change isn't done until its docs are updated in the same pass — see
[`AGENTS.md`](../AGENTS.md). Bump `updated`. Keep it honest: `status: shipped`
means it's on `main` and verified.
