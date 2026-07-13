"""Runs before uniapp_ytdlp imports yt-dlp, to optionally shadow the bundled
yt-dlp with a newer wheel downloaded at runtime (see YtDlpUpdater).

yt-dlp is pure Python and breaks whenever YouTube changes, but ships fixes
almost daily. Prepending a downloaded wheel to sys.path lets a fix take effect
on the next app start without rebuilding the app.
"""
import sys


def activate(wheel_path):
    """Prepend a yt-dlp wheel so `import yt_dlp` resolves to it instead of the
    bundled copy. Returns "true" if the wheel is now the active yt-dlp, "false"
    if it was unusable (caller should delete it) or no wheel was given — either
    way the bundled copy remains importable.
    """
    if not wheel_path:
        return "false"
    sys.path.insert(0, wheel_path)
    try:
        import yt_dlp  # noqa: F401 — probe that the wheel actually imports
        return "true"
    except Exception:
        # Bad or incompatible wheel: undo everything so the bundled copy wins.
        if wheel_path in sys.path:
            sys.path.remove(wheel_path)
        for name in [n for n in sys.modules if n == "yt_dlp" or n.startswith("yt_dlp.")]:
            del sys.modules[name]
        return "false"
