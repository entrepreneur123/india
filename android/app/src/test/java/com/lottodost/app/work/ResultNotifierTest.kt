package com.lottodost.app.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.lottodost.app.data.Draw
import com.lottodost.app.data.Prefs
import com.lottodost.app.data.Tier
import com.lottodost.app.domain.DrawSchedule
import com.lottodost.app.push.Push
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ResultNotifierTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val today = ZonedDateTime.now(DrawSchedule.IST).toLocalDate().toString()
    private val posted get() = shadowOf(ctx.getSystemService(NotificationManager::class.java)).allNotifications.size

    private fun draw(slot: String, date: String = today) = Draw(
        id = "${date}_$slot", slot = slot, date = date, time = "13:00", state = "Nagaland", name = "DEAR WISH SUNDAY",
        format = "D5", price = 6, tiers = listOf(Tier("1", 10_000_000, "FULL", listOf("74D 70698"))))

    @Before fun setUp() {
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ResultNotifier.createChannel(ctx)
        ResultNotifier.notifyAll(ctx, listOf(draw("NL8PM", "2000-01-01")))   // first run: records, posts nothing
    }

    @Test fun pushShowsTheResultAtOnceAndTheFeedDoesNotRepeatIt() {
        ResultNotifier.announce(ctx, "${today}_NL1PM", "NL1PM", "DEAR WISH SUNDAY", "74D 70698")
        assertEquals(1, posted)
        ResultNotifier.announce(ctx, "${today}_NL1PM", "NL1PM", "DEAR WISH SUNDAY", "74D 70698")   // FCM can deliver twice
        ResultNotifier.notifyAll(ctx, listOf(draw("NL1PM")))                                        // then the feed arrives
        assertEquals(1, posted)
        ResultNotifier.notifyAll(ctx, listOf(draw("NL1PM"), draw("SK6PM")))   // a result without a push still notifies
        assertEquals(2, posted)
    }

    @Test fun switchedOffAlertsAndOldMessagesStaySilent() {
        Prefs.setDrawAlert(ctx, "SK6PM", false)
        ResultNotifier.announce(ctx, "${today}_SK6PM", "SK6PM", "DEAR EMPIRE", "93G 38150")
        ResultNotifier.announce(ctx, "2000-01-02_NL1PM", "NL1PM", "DEAR WISH", "74D 70698")
        assertEquals(0, posted)
    }

    @Test fun phoneFollowsOnlyTheDrawsItsOwnerWants() {
        assertEquals(Push.allKeys().toSet(), Push.wantedKeys(ctx))   // every alert is on after install
        Prefs.setDrawAlert(ctx, "NL1PM", false)
        assertEquals(Push.allKeys().toSet() - "NL1PM", Push.wantedKeys(ctx))
        Prefs.setNotificationsEnabled(ctx, false)
        assertEquals(emptySet<String>(), Push.wantedKeys(ctx))
    }
}
