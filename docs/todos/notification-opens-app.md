---
title: Tapping the media notification opens the app
kind: todo
status: shipped
area: playback
priority: high
requested: 2026-07-24
updated: 2026-07-24
---

# Media notification → back into the app

**Ask:** tapping the "player thing" in the notification bar should bring me back
into the app.

The `PlaybackService` (Media3 `MediaSessionService`) shows the media notification,
but tapping it needs a **session activity** PendingIntent to reopen the app /
full player. Set `MediaSession.Builder(...).setSessionActivity(pendingIntent)`
where the PendingIntent launches `MainActivity` (FLAG_ACTIVITY_SINGLE_TOP /
CLEAR_TOP + FLAG_IMMUTABLE). Ideally deep-link straight to the full player for the
current item.

**Done when:** tapping the media notification (lock screen / shade) opens UniApp
(and, ideally, the now-playing full player), verified on-device.
