package com.parentstracker.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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

    fun shareText(context: Context, text: String, title: String = "Share via") {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(send, title))
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
