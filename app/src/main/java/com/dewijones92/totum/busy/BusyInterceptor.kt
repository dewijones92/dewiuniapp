package com.dewijones92.totum.busy

import com.dewijones92.totum.common.Busy
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reports every HTTP call through the shared client as in-flight work.
 *
 * This is the "middleware" half of Dewi's ask: one interceptor covers InnerTube, podcast
 * feeds, SponsorBlock, the iTunes directory and the signature-timestamp fetch, without a
 * single screen having to remember to say it is loading. A per-screen flag is a thing you can
 * forget; a boundary is not.
 *
 * **Long-running transfers deliberately do NOT come through here.** A podcast download runs
 * for minutes on this same client, and an indicator lit for the whole of it says nothing at
 * all — the point is to distinguish "working" from "idle", which a permanently-on bar cannot.
 * `AppContainer` therefore gives the download strategy a client without this interceptor, and
 * downloads keep their own progress UI and notification.
 *
 * Named by host and path so a report can say what the app was waiting on. Query strings are
 * dropped: they carry signatures and tokens, and are never the answer.
 */
class BusyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val handle = Busy.begin("${url.host}${url.encodedPath}")
        try {
            return chain.proceed(chain.request())
        } finally {
            // In a finally because a thrown IOException is the case that most needs it: a
            // failed request that left the app "busy" forever would be worse than no
            // indicator at all.
            handle.close()
        }
    }
}
