package com.lottodost.app.domain

import com.lottodost.app.data.SheetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {
    @Test fun sheetQrIsALinkNotATicket() {
        // Decoded from the Lottery Sambad 8 PM sheet of 18-10-2026 (EN181026.PDF).
        val p = QrPayload.classify("https://whatsapp.com/channel/0029Vb64zKk6mYPVXYMpy41X")
        assertEquals(QrPayload.Link("https://whatsapp.com/channel/0029Vb64zKk6mYPVXYMpy41X", "whatsapp.com"), p)
    }

    @Test fun hostIsShownWithoutWww() {
        val p = QrPayload.classify("https://www.nagalandlotteries.com/verify?t=74D70698") as QrPayload.Link
        assertEquals("nagalandlotteries.com", p.host)
    }

    @Test fun ticketNumberInCodeIsUsedAsTicket() {
        assertEquals(QrPayload.TicketText("74D 70698"), QrPayload.classify(" 74D 70698 "))
    }

    @Test fun anythingElseIsReportedNotGuessed() {
        assertEquals(QrPayload.Other, QrPayload.classify("HELLO 12"))
        assertEquals(QrPayload.Other, QrPayload.classify("https://"))
    }

    @Test fun sambadTodayAndArchiveUrls() {
        val old = "https://www.lotterysambad.com/lotteryoldresultsdown.php?filename=EN181026.PDF"
        val today = "https://www.lotterysambad.com/fetchtoday.php?filename=EN181026.PDF"
        // Checked 18-10-2026: today's sheet only on fetchtoday.php, earlier ones only on the archive URL.
        assertEquals(today, SheetSource.sambadUrl(old, "2026-10-18", "2026-10-18"))
        assertEquals(old, SheetSource.sambadUrl(old, "2026-10-18", "2026-10-19"))
        assertEquals(old, SheetSource.sambadUrl(today, "2026-10-17", "2026-10-18"))
    }

    @Test fun pdfSignature() {
        assertTrue(SheetSource.isPdf("%PDF-1.7".toByteArray()))
        assertFalse(SheetSource.isPdf("File Not Uploaded. Please Try Again Later!".toByteArray()))
        assertFalse(SheetSource.isPdf(ByteArray(2)))
    }
}
