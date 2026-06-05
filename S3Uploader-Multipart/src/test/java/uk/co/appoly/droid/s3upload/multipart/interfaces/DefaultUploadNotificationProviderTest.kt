package uk.co.appoly.droid.s3upload.multipart.interfaces

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric tests for [DefaultUploadNotificationProvider] (and the [UploadNotificationProvider]
 * default methods): channel creation, notification building with/without progress, foreground
 * info, notification id, and the `simple` factory.
 */
@RunWith(AndroidJUnit4::class)
class DefaultUploadNotificationProviderTest {

	private lateinit var context: Context

	@Before
	fun setUp() {
		context = ApplicationProvider.getApplicationContext()
	}

	private fun progress() = MultipartUploadProgress(
		sessionId = "s1",
		fileName = "video.mp4",
		totalBytes = 1000,
		uploadedBytes = 500,
		totalParts = 4,
		uploadedParts = 2,
		currentPartNumber = 3,
		currentPartProgress = 0.5f,
		overallProgress = 50f,
		status = UploadSessionStatus.IN_PROGRESS
	)

	@Test
	fun `createNotificationChannel and createNotification with progress`() {
		val provider = DefaultUploadNotificationProvider()
		provider.createNotificationChannel(context)
		val notification = provider.createNotification(context, "s1", progress())
		assertNotNull(notification)
	}

	@Test
	fun `createNotification without progress uses the indeterminate path`() {
		val provider = DefaultUploadNotificationProvider(showProgress = true, ongoingNotification = false)
		provider.createNotificationChannel(context)
		assertNotNull(provider.createNotification(context, "s1", null))
	}

	@Test
	fun `createForegroundInfo and notification id use the default interface behaviour`() {
		val provider = DefaultUploadNotificationProvider()
		provider.createNotificationChannel(context)
		val fgInfo = provider.createForegroundInfo(context, "session-x", progress())
		assertNotNull(fgInfo)
		assertEquals("session-x".hashCode(), provider.getNotificationId("session-x"))
	}

	@Test
	fun `simple factory builds a provider whose title and content render`() {
		val provider = DefaultUploadNotificationProvider.simple(
			channelId = "my_channel",
			channelName = "My Uploads",
			smallIconResId = android.R.drawable.stat_sys_upload,
			title = "Custom upload",
			contentText = "Working"
		)
		provider.createNotificationChannel(context)
		assertNotNull(provider.createNotification(context, "s1", progress()))
		assertNotNull(provider.createNotification(context, "s1", null))
		assertEquals("my_channel", provider.channelId)
	}
}
