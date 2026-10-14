package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Opens the system share sheet with plain text (used for the Daily Challenge result grid). */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Share your result"))
    } catch (e: ActivityNotFoundException) {
        // No app can handle sharing; nothing to do.
    }
}
