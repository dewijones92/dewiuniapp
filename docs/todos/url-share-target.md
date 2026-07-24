---
title: Register app as share target for URLs
kind: todo
status: open
area: integration
priority: medium
requested: 2026-07-24
updated: 2026-07-24
---

# Share-target / link handler for URLs

**Ask:** when sharing a URL from a browser, our app should appear as an option.

**Approach:** add manifest `<intent-filter>`s on `MainActivity`:

- `ACTION_SEND` `text/plain` — appear in the share sheet.
- `ACTION_VIEW` `http(s)` for `youtube.com` / `youtu.be` (and podcast feed URLs?)
  — become a link handler.

Then handle the incoming intent in `MainActivity`: parse the URL and route it via
the existing seams — a video watch URL → resolve + play; a channel URL → channel
page; a podcast feed URL → subscribe/preview. Keep routing unified (one intent →
`HttpUrl` → the same resolve/open paths the in-app UI uses).

**Done when:** sharing a YouTube link from a browser offers UniApp, and picking
it opens/plays the right thing.
