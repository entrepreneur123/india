package com.yaari.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yaari.chat.model.Media
import com.yaari.chat.model.MediaKind
import com.yaari.chat.model.Message
import com.yaari.chat.ui.Bubble
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
}
