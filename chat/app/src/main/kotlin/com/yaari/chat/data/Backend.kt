package com.yaari.chat.data

import com.yaari.chat.model.Profile
import com.yaari.chat.model.Target

sealed interface PartnerEvent {
    data class Reply(val text: String) : PartnerEvent
    data object Left : PartnerEvent
    data object Silent : PartnerEvent
}

/**
 * Everything the app needs from the server. The prototype runs on [FakeBackend];
 * v1 swaps in Firebase Phone Auth + Firestore (chat) + a matchmaking service, and LiveKit for video.
 */
interface Backend {
    suspend fun sendOtp(phone: String)
    suspend fun verifyOtp(phone: String, code: String): Boolean

    /** One phone number = one account: an existing account logs back in instead of creating a new one. */
    fun accountFor(phone: String): Profile?
    fun register(phone: String, profile: Profile)

    /** Null when nobody matching [target] is online. */
    suspend fun findPartner(me: Profile, target: Target, exclude: Set<String>): Profile?
    suspend fun partnerReact(partner: Profile, incoming: String?, canLeave: Boolean): PartnerEvent
    suspend fun answerVideoRequest(partner: Profile): Boolean
}
