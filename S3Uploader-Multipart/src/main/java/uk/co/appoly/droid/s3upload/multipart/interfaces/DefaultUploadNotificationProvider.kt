package uk.co.appoly.droid.s3upload.multipart.interfaces

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress

/**
 * Configurable default implementation of [UploadNotificationProvider].
 *
 * This class provides a convenient way to customize upload notifications without
 * implementing the full interface. Common properties like channel ID, icon, and
 * text content can be configured via constructor parameters or lambdas.
 *
 * ## Example Usage
 *
 * ```kotlin
 * // Simple customization
 * val provider = DefaultUploadNotificationProvider(
 *     channelId = "my_uploads",
 *     channelName = "File Uploads",
 *     smallIconResId = R.drawable.ic_upload
 * )
 *
 * // Advanced customization with dynamic text
 * val provider = DefaultUploadNotificationProvider(
 *     channelId = "my_uploads",
 *     channelName = "File Uploads",
 *     smallIconResId = R.drawable.ic_upload,
 *     titleProvider = { progress ->
 *         "Uploading ${progress?.fileName ?: "file"}..."
 *     },
 *     contentTextProvider = { progress ->
 *         progress?.let {
 *             "${it.uploadedParts}/${it.totalParts} parts • ${it.overallProgress.toInt()}%"
 *         } ?: "Preparing upload..."
 *     }
 * )
 * ```
 *
 * @property channelId Notification channel ID (must be unique to your app)
 * @property channelName User-visible name for the notification channel
 * @property channelDescription Optional description shown in notification settings
 * @property channelImportance Notification importance level (default: [NotificationManager.IMPORTANCE_LOW])
 * @property smallIconResId Resource ID for the notification small icon
 * @property titleProvider Lambda to generate notification title from progress
 * @property contentTextProvider Lambda to generate notification content text from progress
 * @param serviceType Foreground service type for Android Q+ (default: DATA_SYNC)
 * @property ongoingNotification Whether the notification should be ongoing (default: true)
 * @property showProgress Whether to show a progress bar (default: true)
 */
class DefaultUploadNotificationProvider(
	val channelId: String = DEFAULT_CHANNEL_ID,
	val channelName: String = DEFAULT_CHANNEL_NAME,
	val channelDescription: String? = DEFAULT_CHANNEL_DESCRIPTION,
	val channelImportance: Int = DEFAULT_CHANNEL_IMPORTANCE,
	@param:DrawableRes val smallIconResId: Int = android.R.drawable.stat_sys_upload,
	val titleProvider: (MultipartUploadProgress?) -> String = { progress ->
		progress?.fileName?.let { "Uploading $it" } ?: "Uploading file"
	},
	val contentTextProvider: (MultipartUploadProgress?) -> String = { progress ->
		progress?.let {
			"${it.overallProgress.toInt()}% • ${it.uploadedParts}/${it.totalParts} parts"
		} ?: "Upload in progress..."
	},
	private val serviceType: Int = DEFAULT_FOREGROUND_SERVICE_TYPE,
	val ongoingNotification: Boolean = true,
	val showProgress: Boolean = true
) : UploadNotificationProvider {

	@SuppressLint("WrongConstant") // channelImportance uses DEFAULT_CHANNEL_IMPORTANCE which equals IMPORTANCE_LOW
	override fun createNotificationChannel(context: Context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				channelId,
				channelName,
				channelImportance
			).apply {
				channelDescription?.let { description = it }
			}
			val notificationManager = context.getSystemService(NotificationManager::class.java)
			notificationManager?.createNotificationChannel(channel)
		}
	}

	override fun createNotification(
		context: Context,
		sessionId: String,
		progress: MultipartUploadProgress?
	): Notification {
		val builder = NotificationCompat.Builder(context, channelId)
			.setContentTitle(titleProvider(progress))
			.setContentText(contentTextProvider(progress))
			.setSmallIcon(smallIconResId)
			.setOngoing(ongoingNotification)

		if (showProgress) {
			val progressPercent = progress?.overallProgress?.toInt() ?: 0
			val indeterminate = progress == null
			builder.setProgress(100, progressPercent, indeterminate)
		}

		return builder.build()
	}

	override fun getForegroundServiceType(): Int = serviceType

	companion object {
		/** Default notification channel ID */
		const val DEFAULT_CHANNEL_ID = "s3_upload_channel"

		/** Default notification channel name */
		const val DEFAULT_CHANNEL_NAME = "S3 Uploads"

		/** Default notification channel description */
		const val DEFAULT_CHANNEL_DESCRIPTION = "Shows progress of file uploads"

		/**
		 * Default channel importance level.
		 * Value 2 = [NotificationManager.IMPORTANCE_LOW] (API 24+)
		 */
		const val DEFAULT_CHANNEL_IMPORTANCE = 2

		/**
		 * Default foreground service type.
		 * Value 1 = [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC] (API 29+)
		 */
		const val DEFAULT_FOREGROUND_SERVICE_TYPE = 1

		/**
		 * Creates a provider with simple text customization.
		 *
		 * @param channelId Notification channel ID
		 * @param channelName User-visible channel name
		 * @param smallIconResId Small icon resource ID
		 * @param title Static notification title
		 * @param contentText Static content text (progress percentage will be appended)
		 */
		fun simple(
			channelId: String,
			channelName: String,
			@DrawableRes smallIconResId: Int,
			title: String = "Uploading file",
			contentText: String = "Upload in progress"
		): DefaultUploadNotificationProvider = DefaultUploadNotificationProvider(
			channelId = channelId,
			channelName = channelName,
			smallIconResId = smallIconResId,
			titleProvider = { title },
			contentTextProvider = { progress ->
				progress?.let { "$contentText - ${it.overallProgress.toInt()}%" }
					?: contentText
			}
		)
	}
}
