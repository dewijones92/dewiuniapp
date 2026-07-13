"""mitmproxy addon: strip auth/cookie/identity headers so a recorded cassette
never contains personal credentials. Runs during recording."""
from mitmproxy import http

SENSITIVE = {
    "cookie", "set-cookie", "authorization", "x-goog-authuser",
    "x-goog-visitor-id", "x-youtube-identity-token", "x-goog-pageid",
}


def _scrub(headers):
    for h in list(headers):
        if h.lower() in SENSITIVE:
            del headers[h]


def request(flow: http.HTTPFlow):
    _scrub(flow.request.headers)


def response(flow: http.HTTPFlow):
    if flow.response:
        _scrub(flow.response.headers)
