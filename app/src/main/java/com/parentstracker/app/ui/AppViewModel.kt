package com.parentstracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parentstracker.app.BuildConfig
import com.parentstracker.app.appContainer
import com.parentstracker.app.data.CloudStatus
import com.parentstracker.app.data.Invite
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.MemberLabel
import com.parentstracker.app.data.ShareDirection
import com.parentstracker.app.data.UserSettings
import com.parentstracker.app.data.Viewer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One ViewModel shared by all screens; it forwards to [com.parentstracker.app.data.FamilyRepository]. */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val repository = container.repository

    val settings: StateFlow<UserSettings?> = repository.settingsState
    val members: StateFlow<List<Member>> = repository.members
    val viewers: StateFlow<List<Viewer>> = repository.viewers
    val invites: StateFlow<List<Invite>> = repository.invites
    val cloudStatus: StateFlow<CloudStatus> = repository.cloudStatus
    val cloudConfigured: Boolean = repository.cloud.isConfigured
    val mapsConfigured: Boolean = BuildConfig.MAPS_KEY_CONFIGURED

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private fun say(message: String) {
        _messages.tryEmit(message)
    }

    fun completeOnboarding(name: String) {
        viewModelScope.launch { repository.completeOnboarding(name.ifBlank { "Me" }) }
    }

    fun refreshLocation(silent: Boolean = false) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            repository.refreshMyLocation().onFailure { if (!silent) say(it.message ?: "Couldn't get your location") }
            _refreshing.value = false
        }
    }

    fun refreshIfStale() {
        val last = settings.value?.lastLocation
        if (last == null || System.currentTimeMillis() - last.timeMillis > 2 * 60_000) refreshLocation(silent = true)
    }

    fun onPermissionsChanged() {
        container.refreshSharing()
        refreshIfStale()
    }

    fun setSharingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSharingEnabled(enabled)
            say(if (enabled) "Location sharing resumed" else "Location sharing paused")
        }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch { repository.setInterval(minutes) }
    }

    fun setName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.setName(name.trim()) }
    }

    fun triggerSos() {
        viewModelScope.launch {
            repository.triggerSos()
            say(if (cloudConfigured && viewers.value.isNotEmpty()) "SOS sent to your family" else "SOS is on")
        }
    }

    fun cancelSos() {
        viewModelScope.launch {
            repository.cancelSos()
            say("Glad you're safe. SOS turned off.")
        }
    }

    fun checkIn(message: String) {
        viewModelScope.launch {
            repository.checkIn(message)
            say(if (cloudConfigured && viewers.value.isNotEmpty()) "Sent “$message” to your family" else "Checked in: $message")
        }
    }

    suspend fun createInvite(name: String, relation: String, phone: String?, direction: ShareDirection): Result<Invite> =
        repository.createInvite(name.trim(), relation, phone?.trim()?.ifBlank { null }, direction)

    suspend fun findInvite(code: String): Result<Invite?> = repository.findInvite(code)

    suspend fun acceptInvite(invite: Invite, theirRelation: String?): Result<Unit> =
        repository.acceptInvite(invite, theirRelation).onSuccess {
            say(
                when (invite.direction) {
                    ShareDirection.VIEW_THEM -> "${invite.fromName} can now see your location"
                    ShareDirection.SHARE_MINE -> "You can now see ${invite.fromName}'s location"
                    ShareDirection.MUTUAL -> "You and ${invite.fromName} can now see each other"
                },
            )
        }

    suspend fun declineInvite(invite: Invite): Result<Unit> =
        repository.declineInvite(invite).onSuccess { say("Invite declined. Nothing was shared.") }

    fun cancelInvite(code: String) {
        viewModelScope.launch { repository.cancelInvite(code).onFailure { say(it.message ?: "Couldn't remove invite") } }
    }

    fun stopSharingWith(viewer: Viewer) {
        viewModelScope.launch {
            repository.stopSharingWith(viewer)
                .onSuccess { say("${viewer.name} can no longer see your location") }
                .onFailure { say(it.message ?: "Couldn't stop sharing") }
        }
    }

    fun stopViewing(member: Member) {
        viewModelScope.launch {
            repository.stopViewing(member)
                .onSuccess { say("Removed ${member.displayName}") }
                .onFailure { say(it.message ?: "Couldn't remove ${member.displayName}") }
        }
    }

    fun updateLabel(uid: String, nickname: String, relation: String?, phone: String) {
        viewModelScope.launch {
            repository.updateLabel(
                uid,
                MemberLabel(nickname.trim().ifBlank { null }, relation, phone.trim().ifBlank { null }),
            )
        }
    }

    fun addPlaceHere(name: String, radiusMeters: Int) {
        viewModelScope.launch {
            repository.addPlaceHere(name.trim(), radiusMeters)
                .onSuccess { say("Saved “${name.trim()}”") }
                .onFailure { say(it.message ?: "Couldn't save place") }
        }
    }

    fun removePlace(id: String) {
        viewModelScope.launch { repository.removePlace(id) }
    }

    fun addEmergencyNumber(number: String) {
        val cleaned = number.filter { it.isDigit() || it == '+' }
        if (cleaned.length < 3) {
            say("Please enter a valid phone number")
            return
        }
        viewModelScope.launch { repository.addEmergencyNumber(cleaned) }
    }

    fun removeEmergencyNumber(number: String) {
        viewModelScope.launch { repository.removeEmergencyNumber(number) }
    }

    fun setEmergencyDialNumber(number: String) {
        val cleaned = number.filter { it.isDigit() || it == '+' }
        if (cleaned.isEmpty()) return
        viewModelScope.launch { repository.setEmergencyDialNumber(cleaned) }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDemoMode(enabled) }
    }

    fun setArrivalAlerts(enabled: Boolean) {
        viewModelScope.launch { repository.setArrivalAlerts(enabled) }
    }

    fun setLowBatteryAlerts(enabled: Boolean) {
        viewModelScope.launch { repository.setLowBatteryAlerts(enabled) }
    }
}
