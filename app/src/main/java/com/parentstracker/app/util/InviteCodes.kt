package com.parentstracker.app.util

import com.parentstracker.app.data.Invite
import com.parentstracker.app.data.ShareDirection
import java.security.SecureRandom
import java.util.Random

object InviteCodes {
    /** No 0/O or 1/I, so codes are easy to read out over the phone. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6
    const val VALID_FOR_MS = 48 * 60 * 60_000L

    private val secureRandom = SecureRandom()

    fun generate(random: Random = secureRandom): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Uppercases and drops anything that can't be part of a code (spaces, dashes...). */
    fun normalize(input: String): String = input.uppercase().filter { it in ALPHABET }.take(LENGTH)

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    fun link(code: String) = "parentstracker://join?code=$code"

    fun message(invite: Invite): String = buildString {
        append("Hi ${invite.inviteeName}! ")
        append(
            when (invite.direction) {
                ShareDirection.VIEW_THEM -> "I'd like to see your location on Parents Tracker so I know you're safe."
                ShareDirection.SHARE_MINE -> "I'd like to share my location with you on Parents Tracker."
                ShareDirection.MUTUAL -> "Let's share our locations with each other on Parents Tracker."
            },
        )
        append("\n\nOpen the app → People → Enter code, and type: ${invite.code}")
        append("\nOr tap: ${link(invite.code)}")
        append("\n\nYou'll see exactly what is shared before you agree, and you can stop anytime.")
    }
}
