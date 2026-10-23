package com.yaari.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.ui.Bubble
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BubbleTest {
    @get:Rule val compose = createComposeRule()

    private fun photo(fromMe: Boolean) = Message(1, fromMe, null, Media("content://test/photo.jpg", MediaKind.IMAGE), 0)

    @Test fun receivedPhotoIsHiddenUntilTapped() {
        compose.setContent { Bubble(photo(fromMe = false)) }
        compose.onNodeWithText("Tap to view").performClick()
        compose.onAllNodesWithText("Tap to view").assertCountEquals(0)
    }

    @Test fun ownPhotoIsShownDirectly() {
        compose.setContent { Bubble(photo(fromMe = true)) }
        compose.onAllNodesWithText("Tap to view").assertCountEquals(0)
    }

    @Test fun ownMessageShowsSentThenReadTick() {
        val text = Message(2, true, "Hello", null, 0)
        var read by mutableStateOf(false)
        compose.setContent { Bubble(text, read = read) }
        compose.onNodeWithContentDescription("Sent").assertExists()
        read = true
        compose.onNodeWithContentDescription("Read").assertExists()
    }

    @Test fun receivedMessageHasNoTicks() {
        compose.setContent { Bubble(Message(3, false, "Hi", null, 0)) }
        compose.onAllNodesWithContentDescription("Sent").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Read").assertCountEquals(0)
    }
}
