package com.lottodost.app.domain

import com.lottodost.app.data.Announcement
import com.lottodost.app.data.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustTest {
    @Test fun tamperedConfigCannotRedirectTheApp() {
        val cfg = RemoteConfig(
            mirrors = listOf("https://evil.example/v2/", "http://lottodost.pages.dev/v2/", "https://lottodost.pages.dev/v2/"),
            liveDrawUrl = "intent://pay#Intent;scheme=upi;end",
            officialResultsUrl = "https://www.nagalandlotteries.com/results.php",
            announcement = Announcement("Pay ₹99 fee to claim", "upi://pay?pa=evil@upi&am=99"),
            claimFormUrl = "https://nagalandlotteries.com.evil.example/claim",
        ).trusted()
        assertEquals(listOf("https://lottodost.pages.dev/v2/"), cfg.mirrors)
        assertNull(cfg.liveDrawUrl)
        assertEquals("https://www.nagalandlotteries.com/results.php", cfg.officialResultsUrl)
        assertNull(cfg.announcement!!.url)
        assertNull(cfg.claimFormUrl)
    }

    @Test fun shippedConfigSurvivesUnchanged() {
        // pipeline/app_config.json as published on 25-10-2026 (own domain added)
        val cfg = RemoteConfig(
            mirrors = listOf("https://entrepreneur123.github.io/india/v2/", "https://lottodost.com/v2/", "https://lottodost.pages.dev/v2/",
                "https://cdn.jsdelivr.net/gh/entrepreneur123/india@data/v2/", "https://raw.githubusercontent.com/entrepreneur123/india/data/v2/"),
            liveDrawUrl = "https://www.nagalandlotteries.com/livedraw/index.php",
            officialResultsUrl = "https://www.nagalandlotteries.com/results.php",
            claimFormUrl = "https://www.nagalandlotteries.com/prize-claim-form.php",
        )
        assertEquals(cfg, cfg.trusted())
    }

    @Test fun onlyWebLinksOpen() {
        assertTrue(Trust.isWebLink("https://whatsapp.com/channel/x"))
        assertTrue(Trust.isWebLink("http://example.com/"))
        assertFalse(Trust.isWebLink("upi://pay?pa=a@b"))
        assertFalse(Trust.isWebLink("intent://x#Intent;end"))
        assertFalse(Trust.isWebLink("file:///sdcard/x.pdf"))
        assertFalse(Trust.isWebLink("javascript:alert(1)"))
    }

    @Test fun feedValuesThatBecomeFileNames() {
        assertTrue(Trust.isDate("2026-10-19"))
        assertFalse(Trust.isDate("../../shared_prefs/x"))
        assertTrue(Trust.isId("2026-10-19_NL1PM"))
        assertFalse(Trust.isId("../x"))
    }
}
