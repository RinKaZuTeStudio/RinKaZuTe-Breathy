package com.breathy.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

/**
 * A lightweight image-loading Composable that replaces Coil's AsyncImage.
 *
 * Supports two model types:
 * - **String** (URL): Loaded from network via OkHttp (already a project dependency).
 * - **Uri** (local content): Loaded via ContentResolver (for camera/gallery picks).
 *
 * No external image-loading library required.
 */
@Composable
fun NetworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var bitmap by remember(model) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(model) {
        model ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val loaded: Bitmap? = when (model) {
                    is String -> loadFromUrl(model)
                    is Uri -> loadFromUri(context.contentResolver.openInputStream(model))
                    else -> null
                }
                bitmap = loaded
            } catch (_: Exception) {
                bitmap = null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
    // If bitmap is null (still loading or failed), nothing is rendered.
    // The calling code typically wraps this in a Card/Box with a fallback icon.
}

// ── Internal helpers ────────────────────────────────────────────────────────

private val okHttpClient by lazy { OkHttpClient() }

private fun loadFromUrl(url: String): Bitmap? {
    val request = Request.Builder().url(url).build()
    val response = okHttpClient.newCall(request).execute()
    val stream: InputStream? = response.body?.byteStream()
    return stream?.use { BitmapFactory.decodeStream(it) }
}

private fun loadFromUri(stream: InputStream?): Bitmap? {
    return stream?.use { BitmapFactory.decodeStream(it) }
}
