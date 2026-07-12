package com.dewijones92.uniapp.ytdlp.chaquopy

import androidx.test.core.app.ApplicationProvider
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ChaquopyYtDlpEngineTest {

    private val engine = ChaquopyYtDlpEngine(ApplicationProvider.getApplicationContext())

    @Test
    fun versionsReportRealRuntimes() = runTest(timeout = 2.minutes) {
        val versions = engine.versions()
        assertTrue("yt-dlp version blank", versions.ytDlp.isNotBlank())
        assertTrue("expected Python 3.12.x, got ${versions.python}", versions.python.startsWith("3.12"))
    }

    @Test
    fun extractOnNonMediaPageReturnsFailureValue() = runTest(timeout = 2.minutes) {
        // example.com has no media; the engine must answer with a Failure value, not crash.
        val result = engine.extract(HttpUrl.of("https://example.com/"))
        assertTrue("expected Failure, got $result", result is ExtractionResult.Failure)
    }
}
