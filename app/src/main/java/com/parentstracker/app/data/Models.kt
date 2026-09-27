package com.parentstracker.app.data

/** A single location fix plus the phone status that goes with it. */
data class LocationSnapshot(
    val lat: Double,
    val lng: Double,
    val accuracy: Float = 0f,
    val timeMillis: Long,
    val speed: Float = 0f,
    val address: String? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
)

/** What an invite asks for, always from the point of view of the person who created it. */
enum class ShareDirection(val inviterSeesInvitee: Boolean, val inviteeSeesInviter: Boolean) {
    /** "I want to see your location" — the usual choice for keeping an eye on parents. */
    VIEW_THEM(inviterSeesInvitee = true, inviteeSeesInviter = false),

    /** "I want you to see my location." */
    SHARE_MINE(inviterSeesInvitee = false, inviteeSeesInviter = true),

    /** "Let's see each other." */
    MUTUAL(inviterSeesInvitee = true, inviteeSeesInviter = true),
}

enum class InviteStatus { PENDING, ACCEPTED, DECLINED }

data class Invite(
    val code: String,
    val fromUid: String,
    val fromName: String,
    val inviteeName: String,
    val relation: String,
    val phone: String?,
    val direction: ShareDirection,
    val status: InviteStatus,
    val createdAt: Long,
    val expiresAt: Long,
    val acceptedBy: String? = null,
    val acceptedName: String? = null,
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        status == InviteStatus.PENDING && now > expiresAt
}

/** Someone whose location this user is allowed to see. */
data class Member(
    val uid: String,
    val name: String,
    val nickname: String? = null,
    val relation: String? = null,
    val phone: String? = null,
    val location: LocationSnapshot?,
    val sharing: Boolean = true,
    val sos: Boolean = false,
    val sosAt: Long = 0,
    val checkInMessage: String? = null,
    val checkInAt: Long = 0,
    val isDemo: Boolean = false,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: name
}

/** Someone who is allowed to see this user's location. */
data class Viewer(
    val uid: String,
    val name: String,
    val relation: String? = null,
) {
    val label: String get() = if (relation.isNullOrBlank()) name else "$name ($relation)"
}

data class SavedPlace(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
)

/** Local-only details the user attaches to a family member (never uploaded). */
data class MemberLabel(
    val nickname: String? = null,
    val relation: String? = null,
    val phone: String? = null,
)

sealed interface AppEvent {
    data class InviteAccepted(val name: String) : AppEvent
}

val RELATIONS = listOf("Mom", "Dad", "Grandma", "Grandpa", "Son", "Daughter", "Spouse", "Sibling", "Friend", "Other")

val CHECK_IN_MESSAGES = listOf("I'm OK 👍", "Reached home 🏠", "On my way 🚶", "Please call me 📞")
