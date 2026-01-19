package uk.co.appoly.droid.s3upload.multipart.interfaces

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress

/**
 * Interface for customizing upload notifications.
 *
 * Implement this interface to provide custom notification appearance during uploads.
 * This allows apps to use their own branding, icons, and progress text.
 *
 * ## Example Usage
 *
 * ```kotlin
 * val config = MultipartUploadConfig(
 *     notificationProvider = object : UploadNotificationProvider {
 *         override fun createNotificationChannel(context: Context) {
 *             // Create your notification channel
 *         }
 *
 *         override fun createNotification(
 *             context: Context,
 *             sessionId: String,
 *             progress: MultipartUploadProgress?
 *         ): Notification {
 *             return NotificationCompat.Builder(context, "my_channel")
 *                 .setContentTitle("Uploading ${progress?.fileName}")
 *                 .setSmallIcon(R.drawable.ic_upload)
 *                 .setProgress(100, progress?.overallProgress?.toInt() ?: 0, progress == null)
 *                 .build()
 *         }
 *     }
 * )
 * ```
 *
 * For simpler customization, consider using [DefaultUploadNotificationProvider] which
 * allows configuring common properties like channel ID, icon, and text providers
 * without implementing the full interface.
 *
 * @see DefaultUploadNotificationProvider
 */
interface UploadNotificationProvider {

	/**
	 * Creates the notification channel for upload notifications.
	 *
	 * Called once when the upload worker starts. On API < 26, this method
	 * should be a no-op as notification channels don't exist.
	 *
	 * @param context Application context
	 */
	fun createNotificationChannel(context: Context)

	/**
	 * Creates a notification for the upload.
	 *
	 * Called when starting the foreground service and periodically during uploads
	 * to update progress.
	 *
	 * @param context Application context
	 * @param sessionId The upload session ID
	 * @param progress Current upload progress, or null if progress is not yet available
	 * @return The notification to display
	 */
	fun createNotification(
		context: Context,
		sessionId: String,
		progress: MultipartUploadProgress?
	): Notification

	/**
	 * Gets the notification ID for a given session.
	 *
	 * Each upload session should have a unique notification ID to allow
	 * independent notification updates.
	 *
	 * Default implementation uses [sessionId.hashCode()].
	 *
	 * @param sessionId The upload session ID
	 * @return A unique notification ID for this session
	 */
	fun getNotificationId(sessionId: String): Int = sessionId.hashCode()

	/**
	 * Gets the foreground service type for the upload worker.
	 *
	 * Used on Android Q+ to specify the type of foreground service.
	 * Default is [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC].
	 *
	 * @return The foreground service type
	 */
	fun getForegroundServiceType(): Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

	/**
	 * Creates a [ForegroundInfo] for the upload worker.
	 *
	 * Default implementation combines [createNotification], [getNotificationId],
	 * and [getForegroundServiceType]. Override only if you need custom behavior.
	 *
	 * @param context Application context
	 * @param sessionId The upload session ID
	 * @param progress Current upload progress, or null if not yet available
	 * @return ForegroundInfo for the worker
	 */
	fun createForegroundInfo(
		context: Context,
		sessionId: String,
		progress: MultipartUploadProgress?
	): ForegroundInfo {
		return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
			ForegroundInfo(
				getNotificationId(sessionId),
				createNotification(context, sessionId, progress),
				getForegroundServiceType()
			)
		} else {
			ForegroundInfo(
				getNotificationId(sessionId),
				createNotification(context, sessionId, progress)
			)
		}
	}
}
