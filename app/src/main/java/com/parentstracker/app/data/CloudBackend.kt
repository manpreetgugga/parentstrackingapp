package com.parentstracker.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

sealed interface CloudStatus {
    data object NotConfigured : CloudStatus
    data object Connecting : CloudStatus
    data class Connected(val uid: String) : CloudStatus
    data class Error(val message: String) : CloudStatus
}

/** A family member's shared state as stored in the cloud. */
data class RemoteMember(
    val uid: String,
    val name: String,
    val location: LocationSnapshot?,
    val sharing: Boolean,
    val sos: Boolean,
    val sosAt: Long,
    val checkInMessage: String?,
    val checkInAt: Long,
)

/** This user's own cloud document: mainly who is allowed to see them. */
data class MyCloudProfile(val viewers: List<Viewer> = emptyList())

/** Partial update of this user's shared state; null fields are left untouched. */
data class MyStateUpdate(
    val name: String? = null,
    val location: LocationSnapshot? = null,
    val sharing: Boolean? = null,
    val sos: Boolean? = null,
    val sosAt: Long? = null,
    val checkInMessage: String? = null,
    val checkInAt: Long? = null,
)

/**
 * Moves locations and invites between family phones.
 * [FirebaseBackend] is used when google-services.json is present, otherwise [LocalOnlyBackend].
 */
interface CloudBackend {
    val isConfigured: Boolean
    val status: StateFlow<CloudStatus>

    /** People whose location this user may see. */
    val members: Flow<List<RemoteMember>>

    /** This user's own document, or null while it is still loading. */
    val myProfile: Flow<MyCloudProfile?>

    /** Invites this user created. */
    val myInvites: Flow<List<Invite>>

    suspend fun connect()
    suspend fun publishState(update: MyStateUpdate)
    suspend fun createInvite(fromName: String, inviteeName: String, relation: String, phone: String?, direction: ShareDirection): Invite
    suspend fun findInvite(code: String): Invite?
    suspend fun acceptInvite(invite: Invite, myName: String)
    suspend fun declineInvite(invite: Invite, myName: String)
    suspend fun deleteInvite(code: String)
    suspend fun addViewer(uid: String, name: String)
    suspend fun removeViewer(uid: String)
    suspend fun stopViewing(ownerUid: String)
}

class CloudNotConfiguredException : IllegalStateException(
    "Family sync isn't set up yet. Add google-services.json (see README) to invite family on other phones.",
)

/** Used until Firebase is configured: the app still works for the user's own location, map, places and SOS. */
class LocalOnlyBackend : CloudBackend {
    override val isConfigured = false
    override val status: StateFlow<CloudStatus> = MutableStateFlow(CloudStatus.NotConfigured).asStateFlow()
    override val members: Flow<List<RemoteMember>> = flowOf(emptyList())
    override val myProfile: Flow<MyCloudProfile?> = flowOf(MyCloudProfile())
    override val myInvites: Flow<List<Invite>> = flowOf(emptyList())

    override suspend fun connect() = Unit
    override suspend fun publishState(update: MyStateUpdate) = Unit
    override suspend fun createInvite(
        fromName: String,
        inviteeName: String,
        relation: String,
        phone: String?,
        direction: ShareDirection,
    ): Invite = throw CloudNotConfiguredException()

    override suspend fun findInvite(code: String): Invite? = throw CloudNotConfiguredException()
    override suspend fun acceptInvite(invite: Invite, myName: String) = throw CloudNotConfiguredException()
    override suspend fun declineInvite(invite: Invite, myName: String) = throw CloudNotConfiguredException()
    override suspend fun deleteInvite(code: String) = throw CloudNotConfiguredException()
    override suspend fun addViewer(uid: String, name: String) = throw CloudNotConfiguredException()
    override suspend fun removeViewer(uid: String) = throw CloudNotConfiguredException()
    override suspend fun stopViewing(ownerUid: String) = throw CloudNotConfiguredException()
}
