"""Bridge between ChaquopyYtDlpEngine (Kotlin) and yt-dlp.

Contract: every function returns a JSON string; expected failures are values
({"ok": false, "kind": ...}), never exceptions. Keeping the boundary
string-typed avoids leaking PyObject lifetimes into Kotlin.
"""
import json
import platform

import yt_dlp


def versions():
    return json.dumps({
        "yt_dlp": yt_dlp.version.__version__,
        "python": platform.python_version(),
    })


def extract(url):
    options = {"quiet": True, "no_warnings": True, "skip_download": True}
    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.sanitize_info(ydl.extract_info(url, download=False))
            return json.dumps({"ok": True, "info": info})
    except yt_dlp.utils.DownloadError as e:
        return json.dumps({"ok": False, "kind": _classify(e), "detail": str(e)})


def download(url, target_dir, format_id, listener):
    def hook(d):
        if d.get("status") == "downloading":
            listener.onProgress(
                int(d.get("downloaded_bytes") or 0),
                int(d.get("total_bytes") or d.get("total_bytes_estimate") or 0),
                int(d.get("eta") or 0),
            )

    options = {
        "quiet": True,
        "no_warnings": True,
        "outtmpl": target_dir + "/%(id)s.%(ext)s",
        "progress_hooks": [hook],
    }
    if format_id:
        options["format"] = format_id
    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.sanitize_info(ydl.extract_info(url, download=True))
            path = (info.get("requested_downloads") or [{}])[0].get("filepath")
            return json.dumps({"ok": True, "filepath": path})
    except yt_dlp.utils.DownloadError as e:
        return json.dumps({"ok": False, "kind": _classify(e), "detail": str(e)})


def _classify(error):
    cause = error.exc_info[1] if error.exc_info else error
    if isinstance(cause, yt_dlp.utils.UnsupportedError):
        return "unsupported"
    if isinstance(cause, (OSError, yt_dlp.utils.network_exceptions)):
        return "network"
    return "extractor"
