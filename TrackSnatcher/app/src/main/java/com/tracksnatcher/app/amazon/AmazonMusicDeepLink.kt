package com.tracksnatcher.app.amazon

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tracksnatcher.app.AmazonMusic
import com.tracksnatcher.app.domain.model.Track
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Amazon Music has no public "append to playlist" API, so the fallback is a deep link into
 * the Amazon Music app's search for the matched track. If the app isn't installed we fall
 * back to the Play Store listing.
 */
class AmazonMusicDeepLink @Inject constructor() {

    /** @return true if an Amazon target (app or store) was opened. */
    fun open(context: Context, track: Track): Boolean {
        val query = URLEncoder.encode("${track.title} ${track.artist}", Charsets.UTF_8.name())
        val uri = Uri.parse(AmazonMusic.SEARCH_URI_TEMPLATE.format(query))

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(AmazonMusic.PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            openPlayStore(context)
        }
    }

    private fun openPlayStore(context: Context): Boolean = try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${AmazonMusic.PACKAGE}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
