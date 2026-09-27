package com.parentstracker.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Hands work off to other apps: dialer, SMS, Google Maps, share sheet. None of these need an API key. */
object Intents {

    fun dial(context: Context, number: String) {
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
    }

    fun sms(context: Context, numbers: List<String>, body: String) {
        start(
            context,
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";") { Uri.encode(it) }))
                .putExtra("sms_body", body),
        )
    }

    fun directions(context: Context, lat: Double, lng: Double) {
        val navigation = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Geo.coord(lat)},${Geo.coord(lng)}"))
        if (!start(context, navigation, quiet = true)) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(Geo.directionsLink(lat, lng))))
        }
    }

    fun openInMaps(context: Context, lat: Double, lng: Double, label: String) {
        val point = "${Geo.coord(lat)},${Geo.coord(lng)}"
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point(${Uri.encode(label)})"))
        if (!start(context, geo, quiet = true)) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(Geo.mapsLink(lat, lng))))
        }
    }

    /** Opens Google Maps searching for e.g. "hospital" around a point (or around the phone). */
    fun searchNearby(context: Context, query: String, lat: Double?, lng: Double?) {
        val center = if (lat != null && lng != null) "${Geo.coord(lat)},${Geo.coord(lng)}" else "0,0"
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$center?q=${Uri.encode(query)}"))
        if (!start(context, geo, quiet = true)) {
            val web = if (lat != null && lng != null) {
                "https://www.google.com/maps/search/${Uri.encode(query)}/@$center,14z"
            } else {
                "https://www.google.com/maps/search/${Uri.encode("$query near me")}"
            }
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(web)))
        }
    }

    fun openUrl(context: Context, url: String) {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun shareText(context: Context, text: String, title: String = "Share via") {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(send, title))
    }

    /** Android 12+ screen where the user allows exact alarms for this app. */
    fun exactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
    }

    fun appSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    private fun start(context: Context, intent: Intent, quiet: Boolean = false): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) Toast.makeText(context, "No app found to open this", Toast.LENGTH_SHORT).show()
        false
    }
}
