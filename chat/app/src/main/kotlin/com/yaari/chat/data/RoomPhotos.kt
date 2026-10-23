package com.yaari.chat.data

/**
 * Room photos are shrunk and checked for nudity on this phone before anything is uploaded (web: lib/nudity.ts
 * classifyImage). The live build runs the call check's model ([Nsfw], TensorFlow Lite) on the photo; the demo has no
 * model and lets every photo through.
 */
interface PhotoCheck {
    /** The picked image as a JPEG of at most 1280 px (a file in the app's cache); throws bad_media when it isn't an image. */
    suspend fun shrink(uri: String): String

    /** True when the photo looks like nudity. Throws when the model can't run (the photo isn't sent then either). */
    suspend fun nude(jpegUri: String): Boolean

    /** No model (the demo, tests): the photo is sent as picked. */
    object None : PhotoCheck {
        override suspend fun shrink(uri: String) = uri
        override suspend fun nude(jpegUri: String) = false
    }

    companion object {
        /** The longest side of a sent photo, in pixels (as the web and the chat photos). */
        const val MAX_PX = 1280
    }
}
