package com.parentstracker.app

import com.parentstracker.app.data.Invite
import com.parentstracker.app.data.InviteStatus
import com.parentstracker.app.data.ShareDirection
import com.parentstracker.app.util.InviteCodes
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodesTest {

    @Test
    fun generatedCodesAreValidAndAvoidConfusingCharacters() {
        val random = Random(42)
        repeat(500) {
            val code = InviteCodes.generate(random)
            assertTrue(code, InviteCodes.isValid(code))
            assertFalse(code, code.any { it in "01OI" })
        }
    }

    @Test
    fun normalizeCleansUserInput() {
        assertEquals("ABC234", InviteCodes.normalize(" abc-234 "))
        assertEquals("ABCDEF", InviteCodes.normalize("abcdefgh"))
        assertEquals("", InviteCodes.normalize("0O1I"))
    }

    @Test
    fun rejectsInvalidCodes() {
        assertFalse(InviteCodes.isValid("ABC"))
        assertFalse(InviteCodes.isValid("ABC10O"))
        assertTrue(InviteCodes.isValid("XYZ789"))
    }

    @Test
    fun inviteMessageContainsCodeAndLink() {
        val invite = Invite(
            code = "XYZ789", fromUid = "u1", fromName = "Manpreet", inviteeName = "Mom", relation = "Mom",
            phone = null, direction = ShareDirection.VIEW_THEM, status = InviteStatus.PENDING,
            createdAt = 0, expiresAt = 1,
        )
        val message = InviteCodes.message(invite)
        assertTrue(message.contains("XYZ789"))
        assertTrue(message.contains("parentstracker://join?code=XYZ789"))
        assertTrue(message.startsWith("Hi Mom!"))
    }

    @Test
    fun pendingInviteExpires() {
        val invite = Invite(
            code = "XYZ789", fromUid = "u1", fromName = "A", inviteeName = "B", relation = "",
            phone = null, direction = ShareDirection.MUTUAL, status = InviteStatus.PENDING,
            createdAt = 0, expiresAt = 1_000,
        )
        assertFalse(invite.isExpired(now = 999))
        assertTrue(invite.isExpired(now = 1_001))
        assertFalse(invite.copy(status = InviteStatus.ACCEPTED).isExpired(now = 1_001))
    }
}
