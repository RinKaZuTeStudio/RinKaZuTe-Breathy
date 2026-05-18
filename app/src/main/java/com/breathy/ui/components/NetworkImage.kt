package com.breathy.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.collection.LruCache
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
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

/**
 * A lightweight image-loading Composable that replaces Coil's AsyncImage.
 *
 * Supports three model types:
 * - **String** (URL): Loaded from network via OkHttp (already a project dependency).
 *   Also supports Firebase Storage `gs://` URLs — automatically converts to download URL.
 * - **Uri** (local content): Loaded via ContentResolver (for camera/gallery picks).
 *
 * Features:
 * - **LRU memory cache** (8 MB) — prevents re-fetching on recomposition.
 * - **Downsampling** — large images are scaled down to prevent OOM.
 * - **Firebase Storage** — gs:// URLs are resolved to download URLs automatically.
 * - **Placeholder support** — renders nothing while loading; caller provides fallback.
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
                // Check cache first
                val cacheKey = when (model) {
                    is String -> "url:$model"
                    is Uri -> "uri:$model"
                    else -> null
                }
                if (cacheKey != null) {
                    val cached = imageCache[cacheKey]
                    if (cached != null && !cached.isRecycled) {
                        bitmap = cached
                        return@withContext
                    }
                }

                // Resolve Firebase Storage gs:// URLs to download URLs
                val resolvedUrl = if (model is String && model.startsWith("gs://")) {
                    try {
                        val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(model)
                        storageRef.downloadUrl.await().toString()
                    } catch (e: Exception) {
                        // If Firebase Storage resolution fails, try the URL as-is
                        model
                    }
                } else {
                    model
                }

                val loaded: Bitmap? = when (resolvedUrl) {
                    is String -> loadFromUrl(resolvedUrl)
                    is Uri -> loadFromUri(context.contentResolver.openInputStream(resolvedUrl))
                    else -> null
                }

                // Downsample if the bitmap is very large (prevent OOM)
                val downsampled = loaded?.let { bmp ->
                    val maxDim = 1024
                    if (bmp.width > maxDim || bmp.height > maxDim) {
                        val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
                        val w = (bmp.width * scale).toInt()
                        val h = (bmp.height * scale).toInt()
                        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                        if (scaled !== bmp) bmp.recycle()
                        scaled
                    } else bmp
                }

                // Store in cache
                if (cacheKey != null && downsampled != null) {
                    imageCache.put(cacheKey, downsampled)
                }

                bitmap = downsampled
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

/** LRU memory cache — 8 MB approx (1/8 of available memory on a 64MB heap). */
private val imageCache: LruCache<String, Bitmap> by lazy {
    val maxSize = 8 * 1024 * 1024 // 8 MB
    object : LruCache<String, Bitmap>(maxSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount
        }
    }
}

private fun loadFromUrl(url: String): Bitmap? {
    val request = Request.Builder().url(url).build()
    val response = okHttpClient.newCall(request).execute()
    val stream: InputStream? = response.body?.byteStream()
    return stream?.use { BitmapFactory.decodeStream(it) }
}

private fun loadFromUri(stream: InputStream?): Bitmap? {
    return stream?.use { BitmapFactory.decodeStream(it) }
}
