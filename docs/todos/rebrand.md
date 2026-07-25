---
title: Whole-app rename / rebrand (name, package, colours, icon)
kind: todo
status: refining
area: branding
priority: medium
requested: 2026-07-25
updated: 2026-07-25
---

# Rebrand: name, package, colours, icon

**Ask:** a whole rename/rebrand — full find-and-replace, colours, a new app icon, an
attractively branded app, and replace the package name. "You can make SVGs, right?"

Yes — I can author SVGs and generate the Android vector drawables / adaptive-icon
layers from them, all in-repo with no external tooling.

## The one thing that is genuinely irreversible: `applicationId`

Everything else here is cosmetic and reversible. The **`applicationId`** is not:

- Android identifies an installed app by it. Changing it makes the new build a
  **different app** — it installs alongside the old one, and **does not inherit its
  data**: your subscriptions, queue, playlists, history, downloads and settings all
  live under the old id's data directory.
- **Obtainium** tracks the app by that id too, so it will treat the rename as a new app
  rather than an update.

So the rename must come with a decision:

| Option | Consequence |
|---|---|
| **Keep `applicationId`, rebrand everything else** (my recommendation) | Zero data loss, Obtainium keeps updating, and nobody but you ever sees the id. The *Kotlin package* can still be renamed for tidiness (see below) |
| **Change it and accept a fresh start** | Clean, but you reinstall and lose local state unless we ship an export/import first |
| **Change it with a migration** | Only real option if you want both: export to a file from the old build, import into the new one — which is exactly [the backup/restore feature](feature-gap-review.md) the AI review flagged. Build that *first*, then rename |

**Kotlin package** (`com.dewijones92.uniapp.*`) is a different matter — renaming that is
a safe, mechanical refactor with no user-visible effect, independent of `applicationId`.

## What a rebrand actually touches

1. **Name** — `app_name`, the `<title>` of nothing (no web), README, `AGENTS.md`,
   `CLAUDE.md`, docs, the CI workflow's artefact names, the release notes.
2. **Kotlin package** — ~200 files' `package`/`import` lines, plus `AndroidManifest`
   references and the custom session-command action strings
   (`com.dewijones92.uniapp.SKIP_SILENCE` etc. — these are just strings, but they must
   stay internally consistent).
3. **Colours / theme** — currently Material 3 dynamic colour with a fallback scheme. A
   brand identity means a deliberate seed colour and a considered scheme for light and
   dark, rather than the template default the brief explicitly rules out.
4. **Icon** — an adaptive icon (foreground + background layers, plus a monochrome layer
   for themed icons on Android 13+). This is where SVG authoring earns its place: one
   mark, exported to the three layers.
5. **Signing/release** — the key is tied to the app, not the name, so it survives; but
   the release notes and the `latest` prerelease naming mention the name.

## What I'd want from you before starting

- **The name.** I can propose a shortlist if you'd like, but this is yours.
- **`applicationId`: keep or change?** (Recommendation: keep, unless you want
  backup/restore built first.)
- **A colour direction** — one seed colour, or a mood ("warm and paper-like",
  "dark and neon", "clinical and quiet") and I'll propose schemes.
- **Icon direction** — the two pillars suggest an obvious visual idea (something that
  reads as both "play" and "broadcast"), but a single strong letterform also works.

## Order I'd do it in

1. Name + colours + icon (visible, reversible, no risk).
2. Kotlin package rename (mechanical, gated).
3. `applicationId` **only** if you've chosen to, and only after backup/restore exists.
