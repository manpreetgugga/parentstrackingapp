package com.parentstracker.app.util

import com.parentstracker.app.data.CurfewRule
import com.parentstracker.app.data.Member
import com.parentstracker.app.data.SavedPlace

/** "Expected by" alerts: someone isn't at a saved place by the time you set. */
object Curfews {
    /** Only check for this long after the deadline, so a rule doesn't fire at 3 AM after a late rule. */
    const val CHECK_WINDOW_MINUTES = 3 * 60

    /** Places a fix this far outside the radius still counts as "there", to absorb GPS jitter. */
    private const val SLACK_METERS = 50.0

    /**
     * Members who should be at the rule's place by now but aren't. Only fresh locations count:
     * an old fix can't tell us where someone is now (the no-update alert covers that case).
     */
    fun late(rule: CurfewRule, place: SavedPlace, members: List<Member>, minuteOfDay: Int, now: Long): List<Member> {
        val deadline = rule.hour * 60 + rule.minute
        val sinceDeadline = (minuteOfDay - deadline + 24 * 60) % (24 * 60)
        if (sinceDeadline >= CHECK_WINDOW_MINUTES) return emptyList()
        return members.filter { m ->
            val l = m.location
            (rule.uid == null || rule.uid == m.uid) &&
                !m.isDemo && m.sharing && l != null &&
                now - l.timeMillis < Geo.STALE_AFTER_MS &&
                Geo.distanceMeters(l.lat, l.lng, place.lat, place.lng) > place.radiusMeters + SLACK_METERS
        }
    }
}
