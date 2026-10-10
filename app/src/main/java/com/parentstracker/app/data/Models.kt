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
    val trail: List<TrailPoint> = emptyList(),
    val medical: MedicalInfo? = null,
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

/** One point of someone's path over the last 24 hours. */
data class TrailPoint(
    val lat: Double,
    val lng: Double,
    val time: Long,
    val address: String? = null,
)

/** Emergency details a person chooses to share with their family. */
data class MedicalInfo(
    val bloodGroup: String = "",
    val conditions: String = "",
    val allergies: String = "",
    val medicines: String = "",
    val doctorName: String = "",
    val doctorPhone: String = "",
) {
    val isEmpty: Boolean
        get() = listOf(bloodGroup, conditions, allergies, medicines, doctorName, doctorPhone).all { it.isBlank() }

    /** One line for SOS texts, e.g. "Blood group O+ · Allergies: penicillin". */
    fun summary(): String = listOfNotNull(
        bloodGroup.takeIf { it.isNotBlank() }?.let { "Blood group $it" },
        conditions.takeIf { it.isNotBlank() }?.let { "Conditions: $it" },
        allergies.takeIf { it.isNotBlank() }?.let { "Allergies: $it" },
        medicines.takeIf { it.isNotBlank() }?.let { "Medicines: $it" },
        listOf(doctorName, doctorPhone).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
            ?.let { "Doctor: ${it.joinToString(" ")}" },
    ).joinToString(" · ")
}

enum class ReminderType { MEDICINE, CHECK_IN }

data class Reminder(
    val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val type: ReminderType,
    val enabled: Boolean = true,
    /** Tablets left, when the user tracks stock; "Taken" counts it down and warns before it runs out. */
    val stock: Int? = null,
)

enum class PingResponse(val message: String) {
    OK("I'm OK 👍"),
    CALL_ME("Please call me 📞"),
}

/** An "Are you OK?" request from one family member to another. */
data class Ping(
    val id: String,
    val from: String,
    val fromName: String,
    val to: String,
    val createdAt: Long,
    val response: PingResponse? = null,
    val respondedAt: Long = 0,
)

sealed interface AppEvent {
    data class InviteAccepted(val name: String) : AppEvent
    data class PingAnswered(val name: String, val response: PingResponse) : AppEvent
}

val RELATIONS = listOf("Mom", "Dad", "Grandma", "Grandpa", "Son", "Daughter", "Spouse", "Sibling", "Friend", "Other")

val CHECK_IN_MESSAGES = listOf("I'm OK 👍", "Reached home 🏠", "On my way 🚶", "Please call me 📞")

val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

/** What kind of thing happened, for the activity history and the weekly summary. */
enum class EventKind { SOS, CHECK_IN, MEDICINE_TAKEN, MEDICINE_MISSED, FAMILY_ALERT, JOURNEY, MOOD }

/** One line of the on-phone activity history. Kept only on this phone. */
data class LogEvent(
    val time: Long,
    val kind: EventKind,
    val title: String,
    val detail: String? = null,
)

/** "Going somewhere": where to and when the person expects to arrive. */
data class Journey(
    val destination: String,
    val startedAt: Long,
    val dueAt: Long,
)

/** How someone felt on a given day (epoch day in the phone's time zone). */
data class MoodEntry(val day: Long, val mood: String)

/** "Alert me if [uid] isn't at [placeId] by [hour]:[minute]"; uid null means everyone. */
data class CurfewRule(
    val id: String,
    val placeId: String,
    val uid: String?,
    val hour: Int,
    val minute: Int,
)

/** Mood choices: emoji plus the check-in text family sees. */
val MOODS = listOf(
    "😊" to "Feeling great",
    "🙂" to "Feeling OK",
    "😐" to "So-so today",
    "😟" to "Not feeling good",
    "🤒" to "Feeling unwell",
)

/** Simple mode's language for labels and voice. */
enum class SimpleLanguage(val label: String, val localeTag: String) {
    ENGLISH("English", "en-IN"),
    HINDI("हिंदी", "hi-IN"),
    PUNJABI("ਪੰਜਾਬੀ", "pa-IN"),
}
