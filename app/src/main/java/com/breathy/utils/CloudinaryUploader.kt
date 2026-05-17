package com.breathy.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Handles file uploads to Cloudinary using unsigned upload presets.
 *
 * This replaces Firebase Storage for all image and video uploads.
 * Cloudinary provides 25 GB free storage with automatic image
 * optimization (format conversion, compression, resizing).
 *
 * ## Setup Required
 * Before using this class, you must create an unsigned upload preset
 * in your Cloudinary dashboard:
 * 1. Go to https://console.cloudinary.com/settings/upload_presets
 * 2. Click "Add Upload Preset"
 * 3. Set **Signing Mode** to **Unsigned**
 * 4. Set **Preset Name** to the value of [UPLOAD_PRESET]
 * 5. Optionally restrict allowed formats and max file size
 * 6. Save the preset
 *
 * ## Security Note
 * Only the cloud name and unsigned upload preset are stored in the
 * client app. The API secret is never exposed — unsigned uploads
 * use a server-side preset that controls what can be uploaded.
 */
class CloudinaryUploader(
    private val context: Context
) {

    companion object {
        /** Cloudinary cloud name. */
        const val CLOUD_NAME = "dagth13cr"

        /**
         * Unsigned upload preset name.
         *
         * **IMPORTANT:** You must create this preset in your Cloudinary
         * dashboard (Settings → Upload → Upload Presets → Add Upload Preset).
         * Set the signing mode to "Unsigned" and the name to exactly this value.
         */
        const val UPLOAD_PRESET = "breathy_unsigned"

        /** Base URL for Cloudinary upload API. */
        private const val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/auto/upload"

        /** Base URL for Cloudinary resource delivery. */
        private const val BASE_DELIVERY_URL = "https://res.cloudinary.com/$CLOUD_NAME"

        /** Upload timeout in milliseconds (5 minutes for large videos). */
        private const val UPLOAD_TIMEOUT_MS = 5L * 60L * 1000L

        /** Maximum number of retry attempts per upload. */
        private const val MAX_RETRIES = 3
    }

    /**
     * Result of a successful upload to Cloudinary.
     *
     * @property secureUrl   The HTTPS URL of the uploaded resource.
     * @property publicId    The Cloudinary public ID (path + name, no extension).
     * @property fileSizeBytes The size of the uploaded file in bytes.
     * @property format      The output format (e.g. "jpg", "mp4").
     * @property resourceType The Cloudinary resource type ("image" or "video").
     */
    data class UploadResult(
        val secureUrl: String,
        val publicId: String,
        val fileSizeBytes: Long,
        val format: String,
        val resourceType: String
    )

    // Shared OkHttp client with extended timeouts for large file uploads
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Public API — High-level upload methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Upload a profile image to Cloudinary.
     *
     * The image is uploaded with a deterministic public ID so that
     * re-uploading overwrites the previous image automatically:
     * `breathy/profileImages/{userId}`
     *
     * @param compressedBytes The compressed JPEG image bytes.
     * @param userId          The user's Firebase Auth UID.
     * @return [UploadResult] on success, or `null` on failure.
     */
    suspend fun uploadProfileImage(
        compressedBytes: ByteArray,
        userId: String
    ): UploadResult? = withContext(Dispatchers.IO) {
        val publicId = "breathy/profileImages/$userId"
        uploadBytes(
            bytes = compressedBytes,
            publicId = publicId,
            folder = "breathy/profileImages",
            resourceType = "image",
            fileName = "$userId.jpg"
        )
    }

    /**
     * Upload a profile image from a URI (for UserRepository.updatePhoto).
     *
     * @param imageUri Content URI of the image.
     * @param userId   The user's Firebase Auth UID.
     * @return [UploadResult] on success, or `null` on failure.
     */
    suspend fun uploadProfileImageFromUri(
        imageUri: Uri,
        userId: String
    ): UploadResult? = withContext(Dispatchers.IO) {
        val publicId = "breathy/profileImages/$userId"
        uploadFromUri(
            uri = imageUri,
            publicId = publicId,
            folder = "breathy/profileImages",
            resourceType = "image",
            fileName = "$userId.jpg"
        )
    }

    /**
     * Upload an event check-in video to Cloudinary.
     *
     * @param videoUri  Content URI of the video.
     * @param userId    The user's Firebase Auth UID.
     * @param eventId   The event ID.
     * @param dayNumber The day number within the event.
     * @return [UploadResult] on success, or `null` on failure.
     */
    suspend fun uploadEventVideo(
        videoUri: Uri,
        userId: String,
        eventId: String,
        dayNumber: Int
    ): UploadResult? = withContext(Dispatchers.IO) {
        val publicId = "breathy/event_videos/${userId}_${eventId}_$dayNumber"
        uploadFromUri(
            uri = videoUri,
            publicId = publicId,
            folder = "breathy/event_videos",
            resourceType = "video",
            fileName = "${userId}_${eventId}_$dayNumber.mp4"
        )
    }

    /**
     * Upload an event check-in video from a local file.
     *
     * @param videoFile The local video file.
     * @param userId    The user's Firebase Auth UID.
     * @param eventId   The event ID.
     * @param dayNumber The day number within the event.
     * @return [UploadResult] on success, or `null` on failure.
     */
    suspend fun uploadEventVideoFromFile(
        videoFile: File,
        userId: String,
        eventId: String,
        dayNumber: Int
    ): UploadResult? = withContext(Dispatchers.IO) {
        val publicId = "breathy/event_videos/${userId}_${eventId}_$dayNumber"
        uploadFromFile(
            file = videoFile,
            publicId = publicId,
            folder = "breathy/event_videos",
            resourceType = "video",
            fileName = "${userId}_${eventId}_$dayNumber.mp4"
        )
    }

    /**
     * Upload a story image to Cloudinary.
     *
     * @param imageUri Content URI of the image.
     * @param userId   The user's Firebase Auth UID.
     * @param storyId  The story ID (or a unique identifier).
     * @return [UploadResult] on success, or `null` on failure.
     */
    suspend fun uploadStoryImage(
        imageUri: Uri,
        userId: String,
        storyId: String
    ): UploadResult? = withContext(Dispatchers.IO) {
        val publicId = "breathy/story_images/${userId}_$storyId"
        uploadFromUri(
            uri = imageUri,
            publicId = publicId,
            folder = "breathy/story_images",
            resourceType = "image",
            fileName = "${userId}_$storyId.jpg"
        )
    }

    /**
     * Build an optimized image URL with Cloudinary transformations.
     *
     * Cloudinary can automatically convert to WebP/AVIF, resize,
     * and compress images on-the-fly via URL parameters.
     *
     * @param originalUrl The original Cloudinary URL.
     * @param width       Desired width (0 = auto).
     * @param height      Desired height (0 = auto).
     * @param quality     Compression quality (1-100, or "auto").
     * @return The optimized URL string.
     */
    fun getOptimizedImageUrl(
        originalUrl: String,
        width: Int = 0,
        height: Int = 0,
        quality: String = "auto"
    ): String {
        if (!originalUrl.contains("res.cloudinary.com")) return originalUrl

        val transformations = buildList {
            add("q_$quality")
            add("f_auto") // Automatic format selection (WebP, AVIF, etc.)
            if (width > 0) add("w_$width")
            if (height > 0) add("h_$height")
        }.joinToString(",")

        // Insert transformations into the URL
        // Original: https://res.cloudinary.com/{cloud}/image/upload/{public_id}
        // Modified: https://res.cloudinary.com/{cloud}/image/upload/{transformations}/{public_id}
        return originalUrl.replace(
            "/image/upload/",
            "/image/upload/$transformations/"
        ).replace(
            "/video/upload/",
            "/video/upload/$transformations/"
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Internal upload implementations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Upload raw bytes to Cloudinary with retry logic.
     */
    private suspend fun uploadBytes(
        bytes: ByteArray,
        publicId: String,
        folder: String,
        resourceType: String,
        fileName: String
    ): UploadResult? {
        var lastException: Exception? = null
        var attempt = 0

        while (attempt < MAX_RETRIES) {
            attempt++
            try {
                val result = withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                    doUploadBytes(bytes, publicId, folder, resourceType, fileName)
                }
                if (result != null) {
                    Timber.d(
                        "Cloudinary upload success: publicId=%s, size=%d, attempts=%d",
                        publicId, bytes.size, attempt
                    )
                    return result
                } else {
                    Timber.w("Cloudinary upload timeout on attempt %d/%d for %s", attempt, MAX_RETRIES, publicId)
                    lastException = Exception("Upload timed out after ${UPLOAD_TIMEOUT_MS}ms")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Cloudinary upload attempt %d/%d failed for %s", attempt, MAX_RETRIES, publicId)
            }

            // Exponential backoff: 500ms, 1s, 2s
            if (attempt < MAX_RETRIES) {
                val backoffMs = 500L * (1L shl (attempt - 1))
                try {
                    kotlinx.coroutines.delay(backoffMs)
                } catch (e: CancellationException) {
                    throw e
                }
            }
        }

        Timber.e(lastException, "All %d Cloudinary upload attempts failed for %s", MAX_RETRIES, publicId)
        return null
    }

    /**
     * Upload from a content URI to Cloudinary with retry logic.
     */
    private suspend fun uploadFromUri(
        uri: Uri,
        publicId: String,
        folder: String,
        resourceType: String,
        fileName: String
    ): UploadResult? = withContext(Dispatchers.IO) {
        // Copy URI content to a temp file first (Cloudinary needs a file or bytes)
        val tempFile = try {
            copyUriToTempFile(uri, fileName)
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy URI to temp file: %s", uri)
            return@withContext null
        }

        try {
            uploadFromFile(tempFile, publicId, folder, resourceType, fileName)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Upload from a local file to Cloudinary with retry logic.
     */
    private suspend fun uploadFromFile(
        file: File,
        publicId: String,
        folder: String,
        resourceType: String,
        fileName: String
    ): UploadResult? {
        var lastException: Exception? = null
        var attempt = 0

        while (attempt < MAX_RETRIES) {
            attempt++
            try {
                val result = withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                    doUploadFile(file, publicId, folder, resourceType, fileName)
                }
                if (result != null) {
                    Timber.d(
                        "Cloudinary upload success: publicId=%s, size=%d, attempts=%d",
                        publicId, file.length(), attempt
                    )
                    return result
                } else {
                    Timber.w("Cloudinary upload timeout on attempt %d/%d for %s", attempt, MAX_RETRIES, publicId)
                    lastException = Exception("Upload timed out after ${UPLOAD_TIMEOUT_MS}ms")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Cloudinary upload attempt %d/%d failed for %s", attempt, MAX_RETRIES, publicId)
            }

            if (attempt < MAX_RETRIES) {
                val backoffMs = 1000L * (1L shl (attempt - 1))
                try {
                    kotlinx.coroutines.delay(backoffMs)
                } catch (e: CancellationException) {
                    throw e
                }
            }
        }

        Timber.e(lastException, "All %d Cloudinary upload attempts failed for %s", MAX_RETRIES, publicId)
        return null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Low-level HTTP upload methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Perform the actual byte upload via Cloudinary REST API.
     */
    private fun doUploadBytes(
        bytes: ByteArray,
        publicId: String,
        folder: String,
        resourceType: String,
        fileName: String
    ): UploadResult {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody("application/octet-stream".toMediaType()))
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .addFormDataPart("public_id", publicId)
            .addFormDataPart("folder", folder)
            .build()

        return executeUpload(requestBody)
    }

    /**
     * Perform the actual file upload via Cloudinary REST API.
     */
    private fun doUploadFile(
        file: File,
        publicId: String,
        folder: String,
        resourceType: String,
        fileName: String
    ): UploadResult {
        val mediaType = guessMediaType(fileName)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, file.asRequestBody(mediaType.toMediaType()))
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .addFormDataPart("public_id", publicId)
            .addFormDataPart("folder", folder)
            .build()

        return executeUpload(requestBody)
    }

    /**
     * Execute the HTTP request and parse the Cloudinary response.
     */
    private fun executeUpload(requestBody: MultipartBody): UploadResult {
        val request = Request.Builder()
            .url(UPLOAD_URL)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw CloudinaryUploadException(
                "Cloudinary upload failed with HTTP ${response.code}: $errorBody"
            )
        }

        val responseBody = response.body?.string()
            ?: throw CloudinaryUploadException("Empty response body from Cloudinary")

        return parseUploadResponse(responseBody)
    }

    /**
     * Parse the Cloudinary upload response JSON.
     */
    private fun parseUploadResponse(json: String): UploadResult {
        val jsonObj = JSONObject(json)

        val secureUrl = jsonObj.optString("secure_url", "")
        val publicId = jsonObj.optString("public_id", "")
        val fileSize = jsonObj.optLong("bytes", 0L)
        val format = jsonObj.optString("format", "")
        val resourceType = jsonObj.optString("resource_type", "image")

        if (secureUrl.isBlank()) {
            throw CloudinaryUploadException("Cloudinary response missing secure_url: $json")
        }

        return UploadResult(
            secureUrl = secureUrl,
            publicId = publicId,
            fileSizeBytes = fileSize,
            format = format,
            resourceType = resourceType
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Utility methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Copy content from a URI to a temporary file.
     */
    private fun copyUriToTempFile(uri: Uri, fileName: String): File {
        val tempFile = File.createTempFile("cloudinary_upload_", "_$fileName", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")
        return tempFile
    }

    /**
     * Guess the MIME type from a file name.
     */
    private fun guessMediaType(fileName: String): String {
        return when {
            fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    /**
     * Get the file size for a content URI.
     */
    fun getFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(sizeIndex)
            }
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var total = 0L
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    total += read
                }
                total
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Exception thrown when a Cloudinary upload fails.
     */
    class CloudinaryUploadException(message: String) : Exception(message)
}
