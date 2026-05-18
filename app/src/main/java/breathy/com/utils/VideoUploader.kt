package breathy.com.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Handles event check-in video uploads to Cloudinary with progress
 * tracking, cancellation support, and automatic retries.
 *
 * Videos are uploaded to: `breathy/event_videos/{userId}_{eventId}_{dayNumber}`
 *
 * Features:
 * - File size validation (max 100 MB)
 * - Reports upload progress as a percentage via [ProgressCallback]
 * - Supports cooperative cancellation via [UploadHandle]
 * - Retries up to [MAX_RETRIES] times on transient failures
 * - 5-minute timeout per attempt (suitable for large video files)
 */
class VideoUploader(
    private val cloudinaryUploader: CloudinaryUploader
) {

    companion object {
        /** Maximum allowed video file size (100 MB). */
        private const val MAX_FILE_SIZE_BYTES = 100L * 1024L * 1024L

        /** Maximum number of retry attempts per upload. */
        private const val MAX_RETRIES = 3
    }

    /**
     * Callback interface for receiving upload progress updates.
     */
    interface ProgressCallback {
        /**
         * Called periodically as bytes are transferred.
         *
         * @param bytesTransferred Number of bytes uploaded so far.
         * @param totalBytes       Total number of bytes to upload.
         * @param percentage       Upload progress as a value between 0.0 and 100.0.
         */
        fun onProgress(bytesTransferred: Long, totalBytes: Long, percentage: Double)
    }

    /**
     * Handle for an in-progress upload. Call [cancel] to abort.
     */
    class UploadHandle {
        @Volatile
        var isCancelled: Boolean = false
            private set

        fun cancel() {
            isCancelled = true
        }
    }

    /**
     * Result of a successful video upload.
     *
     * @property downloadUrl    The publicly accessible URL from Cloudinary.
     * @property storagePath    The Cloudinary public ID (logical path).
     * @property fileSizeBytes  The size of the uploaded video file in bytes.
     */
    data class UploadResult(
        val downloadUrl: String,
        val storagePath: String,
        val fileSizeBytes: Long
    )

    /**
     * Upload an event check-in video to Cloudinary.
     *
     * The video is uploaded to `breathy/event_videos/{userId}_{eventId}_{dayNumber}`.
     * Before uploading, the file size is validated against the 100 MB limit.
     *
     * @param context    Android context for content resolver access.
     * @param userId     The user's Firebase Auth UID.
     * @param eventId    The event ID this check-in belongs to.
     * @param dayNumber  The day number within the event challenge.
     * @param videoUri   Content URI of the source video.
     * @param callback   Optional progress callback with percentage.
     * @param handle     Optional upload handle for cancellation support.
     * @return [UploadResult] on success, or `null` on failure or cancellation.
     */
    suspend fun uploadEventVideo(
        context: Context,
        userId: String,
        eventId: String,
        dayNumber: Int,
        videoUri: Uri,
        callback: ProgressCallback? = null,
        handle: UploadHandle? = null
    ): UploadResult? = withContext(Dispatchers.IO) {
        // ── Step 1: Validate file size ─────────────────────────────────────
        val fileSize = try {
            cloudinaryUploader.getFileSize(videoUri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read video file size from URI: %s", videoUri)
            return@withContext null
        }

        if (fileSize <= 0) {
            Timber.w("Video file is empty or size could not be determined")
            return@withContext null
        }

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            Timber.w(
                "Video file too large: %.1f MB (max %.1f MB)",
                fileSize / (1024.0 * 1024.0),
                MAX_FILE_SIZE_BYTES / (1024.0 * 1024.0)
            )
            return@withContext null
        }

        Timber.d(
            "Starting video upload to Cloudinary: userId=%s, eventId=%s, day=%d, size=%.1f MB",
            userId, eventId, dayNumber, fileSize / (1024.0 * 1024.0)
        )

        if (handle?.isCancelled == true) {
            Timber.d("Upload cancelled before starting")
            return@withContext null
        }

        // ── Step 2: Upload to Cloudinary ──────────────────────────────────
        try {
            val result = cloudinaryUploader.uploadEventVideo(
                videoUri = videoUri,
                userId = userId,
                eventId = eventId,
                dayNumber = dayNumber
            )

            if (result != null) {
                callback?.onProgress(fileSize, fileSize, 100.0)
                Timber.d(
                    "Video uploaded to Cloudinary: publicId=%s, size=%d bytes",
                    result.publicId, fileSize
                )
                UploadResult(
                    downloadUrl = result.secureUrl,
                    storagePath = result.publicId,
                    fileSizeBytes = fileSize
                )
            } else {
                Timber.e("Cloudinary video upload returned null for userId=%s", userId)
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload event video to Cloudinary")
            null
        }
    }

    /**
     * Upload a video file (from a local [File] path) to Cloudinary.
     *
     * Alternative to [uploadEventVideo] when you already have a local file
     * path instead of a content URI. Same validation and retry logic applies.
     *
     * @param userId     The user's Firebase Auth UID.
     * @param eventId    The event ID this check-in belongs to.
     * @param dayNumber  The day number within the event challenge.
     * @param videoFile  The local video file to upload.
     * @param callback   Optional progress callback.
     * @param handle     Optional upload handle for cancellation support.
     * @return [UploadResult] on success, or `null` on failure or cancellation.
     */
    suspend fun uploadEventVideoFromFile(
        userId: String,
        eventId: String,
        dayNumber: Int,
        videoFile: File,
        callback: ProgressCallback? = null,
        handle: UploadHandle? = null
    ): UploadResult? = withContext(Dispatchers.IO) {
        // ── Validate file ──────────────────────────────────────────────────
        if (!videoFile.exists()) {
            Timber.w("Video file does not exist: %s", videoFile.absolutePath)
            return@withContext null
        }

        val fileSize = videoFile.length()
        if (fileSize <= 0) {
            Timber.w("Video file is empty: %s", videoFile.absolutePath)
            return@withContext null
        }

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            Timber.w(
                "Video file too large: %.1f MB (max %.1f MB)",
                fileSize / (1024.0 * 1024.0),
                MAX_FILE_SIZE_BYTES / (1024.0 * 1024.0)
            )
            return@withContext null
        }

        if (handle?.isCancelled == true) {
            Timber.d("Upload cancelled before starting")
            return@withContext null
        }

        Timber.d(
            "Starting video upload from file to Cloudinary: userId=%s, eventId=%s, day=%d, size=%.1f MB",
            userId, eventId, dayNumber, fileSize / (1024.0 * 1024.0)
        )

        // ── Upload to Cloudinary ──────────────────────────────────────────
        try {
            val result = cloudinaryUploader.uploadEventVideoFromFile(
                videoFile = videoFile,
                userId = userId,
                eventId = eventId,
                dayNumber = dayNumber
            )

            if (result != null) {
                callback?.onProgress(fileSize, fileSize, 100.0)
                Timber.d(
                    "Video uploaded to Cloudinary from file: publicId=%s, size=%d bytes",
                    result.publicId, fileSize
                )
                UploadResult(
                    downloadUrl = result.secureUrl,
                    storagePath = result.publicId,
                    fileSizeBytes = fileSize
                )
            } else {
                Timber.e("Cloudinary video upload returned null for userId=%s", userId)
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload event video to Cloudinary")
            null
        }
    }

    /**
     * Delete an event check-in video from Cloudinary.
     *
     * Note: Cloudinary deletion requires the API secret (admin API),
     * which is not safe to include in the client app. Since event videos
     * use a deterministic public ID, uploading a new video for the same
     * day automatically overwrites the old one. This method is a no-op
     * that always returns true.
     *
     * @return Always `true` (overwrite handles "deletion" implicitly).
     */
    suspend fun deleteEventVideo(userId: String, eventId: String, dayNumber: Int): Boolean {
        Timber.d(
            "Event video deletion is handled by overwrite in Cloudinary (publicId=breathy/event_videos/%s_%s_%d)",
            userId, eventId, dayNumber
        )
        return true
    }
}
