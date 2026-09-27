package com.parentstracker.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.parentstracker.app.util.InviteCodes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Firestore layout (security rules live in /firestore.rules):
 *  - locations/{uid}: the user's latest location + status, and `viewers` (uids allowed to read it).
 *  - invites/{code}: a pending request between two people, answered by the invitee.
 * Users sign in anonymously, so nobody needs an account or password.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseBackend : CloudBackend {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val uid = MutableStateFlow(auth.currentUser?.uid)
    private val connectLock = Mutex()
    private val _status = MutableStateFlow<CloudStatus>(
        auth.currentUser?.uid?.let { CloudStatus.Connected(it) } ?: CloudStatus.Connecting,
    )

    override val isConfigured = true
    override val status: StateFlow<CloudStatus> = _status.asStateFlow()

    override suspend fun connect() = connectLock.withLock {
        auth.currentUser?.let { user ->
            uid.value = user.uid
            if (_status.value !is CloudStatus.Error) _status.value = CloudStatus.Connected(user.uid)
            return@withLock
        }
        try {
            _status.value = CloudStatus.Connecting
            val user = auth.signInAnonymously().await().user ?: error("Sign-in returned no user")
            uid.value = user.uid
            _status.value = CloudStatus.Connected(user.uid)
        } catch (e: Exception) {
            _status.value = CloudStatus.Error(
                "Couldn't sign in (${e.message}). Check that Anonymous sign-in is enabled in Firebase Authentication.",
            )
            throw e
        }
    }

    private suspend fun requireUid(): String {
        uid.value?.let { return it }
        connect()
        return uid.value ?: throw IllegalStateException("Not signed in to family sync")
    }

    private fun reportError(e: Exception) {
        Log.w(TAG, "Firestore listener failed", e)
        val code = (e as? FirebaseFirestoreException)?.code
        _status.value = CloudStatus.Error(
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Firestore refused access. Publish the rules from firestore.rules in the Firebase console (see README)."
            } else {
                e.message ?: "Family sync error"
            },
        )
    }

    /** A listener delivered data again, so any earlier error is resolved. */
    private fun markHealthy() {
        val me = uid.value ?: return
        if (_status.value is CloudStatus.Error) _status.value = CloudStatus.Connected(me)
    }

    /** Firestore listeners stop for good after an error; re-subscribe with backoff (5 s up to 60 s). */
    private fun <T> Flow<T>.retryOnError(): Flow<T> = retryWhen { _, attempt ->
        delay((5_000L shl attempt.toInt().coerceAtMost(4)).coerceAtMost(60_000L))
        true
    }

    override val members: Flow<List<RemoteMember>> = uid.flatMapLatest { me ->
        if (me == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = db.collection(LOCATIONS)
                    .whereArrayContains(F_VIEWERS, me)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            reportError(error)
                            close(error)
                            return@addSnapshotListener
                        }
                        markHealthy()
                        trySend(snapshot?.documents.orEmpty().map { it.toRemoteMember() })
                    }
                awaitClose { registration.remove() }
            }.retryOnError()
        }
    }

    override val myProfile: Flow<MyCloudProfile?> = uid.flatMapLatest { me ->
        if (me == null) {
            flowOf(null)
        } else {
            callbackFlow {
                val registration = db.collection(LOCATIONS).document(me)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            reportError(error)
                            trySend(MyCloudProfile())
                            close(error)
                            return@addSnapshotListener
                        }
                        markHealthy()
                        trySend(snapshot?.toProfile() ?: MyCloudProfile())
                    }
                awaitClose { registration.remove() }
            }.retryOnError()
        }
    }

    override val myInvites: Flow<List<Invite>> = uid.flatMapLatest { me ->
        if (me == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = db.collection(INVITES)
                    .whereEqualTo("fromUid", me)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            reportError(error)
                            close(error)
                            return@addSnapshotListener
                        }
                        markHealthy()
                        val invites = snapshot?.documents.orEmpty()
                            .mapNotNull { it.toInvite() }
                            .sortedByDescending { it.createdAt }
                        trySend(invites)
                    }
                awaitClose { registration.remove() }
            }.retryOnError()
        }
    }

    override val incomingPings: Flow<List<Ping>> = pingsWhere("to")

    override val outgoingPings: Flow<List<Ping>> = pingsWhere("from")

    private fun pingsWhere(field: String): Flow<List<Ping>> = uid.flatMapLatest { me ->
        if (me == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = db.collection(PINGS)
                    .whereEqualTo(field, me)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            reportError(error)
                            close(error)
                            return@addSnapshotListener
                        }
                        trySend(snapshot?.documents.orEmpty().mapNotNull { it.toPing() }.sortedByDescending { it.createdAt })
                    }
                awaitClose { registration.remove() }
            }.retryOnError()
        }
    }

    override suspend fun sendPing(to: String, fromName: String) {
        val me = requireUid()
        db.collection(PINGS).add(
            mapOf(
                "from" to me,
                "fromName" to fromName,
                "to" to to,
                "type" to "ARE_YOU_OK",
                "createdAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    /** Doesn't wait for the server, so a reply from a notification works offline too. */
    override suspend fun answerPing(id: String, response: PingResponse) {
        requireUid()
        db.collection(PINGS).document(id)
            .update(mapOf("response" to response.name, "respondedAt" to System.currentTimeMillis()))
            .addOnFailureListener { Log.w(TAG, "Answering ping failed", it) }
    }

    override suspend fun deletePing(id: String) {
        requireUid()
        db.collection(PINGS).document(id).delete()
            .addOnFailureListener { Log.w(TAG, "Deleting ping failed", it) }
    }

    /** Fire-and-forget: Firestore queues the write while offline and syncs it later. */
    override suspend fun publishState(update: MyStateUpdate) {
        val me = requireUid()
        val data = buildMap<String, Any?> {
            update.name?.let { put("name", it) }
            update.location?.let { l ->
                put("lat", l.lat)
                put("lng", l.lng)
                put("accuracy", l.accuracy.toDouble())
                put("speed", l.speed.toDouble())
                put("address", l.address)
                put("battery", l.battery)
                put("charging", l.charging)
                put("updatedAt", l.timeMillis)
            }
            update.sharing?.let { put("sharing", it) }
            update.sos?.let { put("sos", it) }
            update.sosAt?.let { put("sosAt", it) }
            update.checkInMessage?.let { put("checkIn", it) }
            update.checkInAt?.let { put("checkInAt", it) }
            update.trail?.let { trail ->
                put("trail", trail.map { p -> mapOf("lat" to p.lat, "lng" to p.lng, "t" to p.time, "a" to p.address) })
            }
            update.medical?.let { m ->
                put(
                    "medical",
                    mapOf(
                        "bloodGroup" to m.bloodGroup,
                        "conditions" to m.conditions,
                        "allergies" to m.allergies,
                        "medicines" to m.medicines,
                        "doctorName" to m.doctorName,
                        "doctorPhone" to m.doctorPhone,
                    ),
                )
            }
        }
        if (data.isEmpty()) return
        db.collection(LOCATIONS).document(me).set(data, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Publishing location failed", it) }
    }

    override suspend fun createInvite(
        fromName: String,
        inviteeName: String,
        relation: String,
        phone: String?,
        direction: ShareDirection,
    ): Invite {
        val me = requireUid()
        repeat(5) {
            val code = InviteCodes.generate()
            val ref = db.collection(INVITES).document(code)
            if (ref.get().await().exists()) return@repeat
            val now = System.currentTimeMillis()
            val invite = Invite(
                code = code,
                fromUid = me,
                fromName = fromName,
                inviteeName = inviteeName,
                relation = relation,
                phone = phone,
                direction = direction,
                status = InviteStatus.PENDING,
                createdAt = now,
                expiresAt = now + InviteCodes.VALID_FOR_MS,
            )
            ref.set(invite.toMap()).await()
            return invite
        }
        throw IllegalStateException("Couldn't create an invite code, please try again")
    }

    override suspend fun findInvite(code: String): Invite? {
        requireUid()
        val snapshot = db.collection(INVITES).document(code).get().await()
        return if (snapshot.exists()) snapshot.toInvite() else null
    }

    override suspend fun acceptInvite(invite: Invite, myName: String) {
        val me = requireUid()
        require(invite.fromUid != me) { "This is your own invite. Send the code to your family member instead." }
        if (invite.direction.inviterSeesInvitee) {
            val data = mapOf(
                "name" to myName,
                "sharing" to true,
                F_VIEWERS to FieldValue.arrayUnion(invite.fromUid),
                F_VIEWER_NAMES to mapOf(invite.fromUid to invite.fromName),
            )
            db.collection(LOCATIONS).document(me).set(data, SetOptions.merge()).await()
        }
        respond(invite, me, myName, InviteStatus.ACCEPTED)
    }

    override suspend fun declineInvite(invite: Invite, myName: String) {
        respond(invite, requireUid(), myName, InviteStatus.DECLINED)
    }

    private suspend fun respond(invite: Invite, me: String, myName: String, status: InviteStatus) {
        db.collection(INVITES).document(invite.code).update(
            mapOf(
                "status" to status.name,
                "acceptedBy" to me,
                "acceptedName" to myName,
                "respondedAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    override suspend fun deleteInvite(code: String) {
        requireUid()
        db.collection(INVITES).document(code).delete()
            .addOnFailureListener { Log.w(TAG, "Deleting invite failed", it) }
    }

    override suspend fun addViewer(uid: String, name: String) {
        val me = requireUid()
        val data = mapOf(
            F_VIEWERS to FieldValue.arrayUnion(uid),
            F_VIEWER_NAMES to mapOf(uid to name),
        )
        db.collection(LOCATIONS).document(me).set(data, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Adding viewer failed", it) }
    }

    override suspend fun removeViewer(uid: String) {
        val me = requireUid()
        db.collection(LOCATIONS).document(me).update(
            mapOf(
                F_VIEWERS to FieldValue.arrayRemove(uid),
                "$F_VIEWER_NAMES.$uid" to FieldValue.delete(),
            ),
        ).await()
    }

    override suspend fun stopViewing(ownerUid: String) {
        val me = requireUid()
        db.collection(LOCATIONS).document(ownerUid).update(
            mapOf(
                F_VIEWERS to FieldValue.arrayRemove(me),
                "$F_VIEWER_NAMES.$me" to FieldValue.delete(),
            ),
        ).await()
    }

    private fun DocumentSnapshot.toRemoteMember(): RemoteMember {
        val lat = getDouble("lat")
        val lng = getDouble("lng")
        val location = if (lat != null && lng != null) {
            LocationSnapshot(
                lat = lat,
                lng = lng,
                accuracy = getDouble("accuracy")?.toFloat() ?: 0f,
                timeMillis = getLong("updatedAt") ?: 0L,
                speed = getDouble("speed")?.toFloat() ?: 0f,
                address = getString("address"),
                battery = getLong("battery")?.toInt(),
                charging = getBoolean("charging") ?: false,
            )
        } else {
            null
        }
        return RemoteMember(
            uid = id,
            name = getString("name") ?: "Family member",
            location = location,
            sharing = getBoolean("sharing") ?: true,
            sos = getBoolean("sos") ?: false,
            sosAt = getLong("sosAt") ?: 0L,
            checkInMessage = getString("checkIn"),
            checkInAt = getLong("checkInAt") ?: 0L,
            trail = (get("trail") as? List<*>).orEmpty().mapNotNull { it.toTrailPoint() },
            medical = (get("medical") as? Map<*, *>)?.let { m ->
                MedicalInfo(
                    bloodGroup = m["bloodGroup"] as? String ?: "",
                    conditions = m["conditions"] as? String ?: "",
                    allergies = m["allergies"] as? String ?: "",
                    medicines = m["medicines"] as? String ?: "",
                    doctorName = m["doctorName"] as? String ?: "",
                    doctorPhone = m["doctorPhone"] as? String ?: "",
                )
            }?.takeUnless { it.isEmpty },
        )
    }

    private fun Any?.toTrailPoint(): TrailPoint? {
        val m = this as? Map<*, *> ?: return null
        val lat = (m["lat"] as? Number)?.toDouble() ?: return null
        val lng = (m["lng"] as? Number)?.toDouble() ?: return null
        val time = (m["t"] as? Number)?.toLong() ?: return null
        return TrailPoint(lat, lng, time, m["a"] as? String)
    }

    private fun DocumentSnapshot.toPing(): Ping? = runCatching {
        Ping(
            id = id,
            from = getString("from")!!,
            fromName = getString("fromName") ?: "Family",
            to = getString("to")!!,
            createdAt = getLong("createdAt") ?: 0L,
            response = getString("response")?.let { r -> runCatching { PingResponse.valueOf(r) }.getOrNull() },
            respondedAt = getLong("respondedAt") ?: 0L,
        )
    }.getOrNull()

    private fun DocumentSnapshot.toProfile(): MyCloudProfile {
        val viewerIds = (get(F_VIEWERS) as? List<*>)?.filterIsInstance<String>().orEmpty()
        val names = get(F_VIEWER_NAMES) as? Map<*, *> ?: emptyMap<String, String>()
        return MyCloudProfile(viewerIds.map { Viewer(it, names[it] as? String ?: "Family member") })
    }

    private fun DocumentSnapshot.toInvite(): Invite? = runCatching {
        Invite(
            code = id,
            fromUid = getString("fromUid")!!,
            fromName = getString("fromName") ?: "Someone",
            inviteeName = getString("inviteeName") ?: "",
            relation = getString("relation") ?: "",
            phone = getString("phone"),
            direction = ShareDirection.valueOf(getString("direction") ?: ShareDirection.VIEW_THEM.name),
            status = InviteStatus.valueOf(getString("status") ?: InviteStatus.PENDING.name),
            createdAt = getLong("createdAt") ?: 0L,
            expiresAt = getLong("expiresAt") ?: 0L,
            acceptedBy = getString("acceptedBy"),
            acceptedName = getString("acceptedName"),
        )
    }.getOrNull()

    private fun Invite.toMap(): Map<String, Any?> = mapOf(
        "fromUid" to fromUid,
        "fromName" to fromName,
        "inviteeName" to inviteeName,
        "relation" to relation,
        "phone" to phone,
        "direction" to direction.name,
        "status" to status.name,
        "createdAt" to createdAt,
        "expiresAt" to expiresAt,
    )

    private companion object {
        const val TAG = "FirebaseBackend"
        const val LOCATIONS = "locations"
        const val INVITES = "invites"
        const val PINGS = "pings"
        const val F_VIEWERS = "viewers"
        const val F_VIEWER_NAMES = "viewerNames"
    }
}
