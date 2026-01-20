package uk.co.appoly.droid.s3upload.multipart.utils

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LogType
import com.duck.flexilogger.LoggerWithLevel
import com.duck.flexilogger.LoggingLevel
import uk.co.appoly.droid.s3upload.S3Uploader

internal val MultipartUploadLog = LoggerWithLevel(LoggingLevel.V, MultipartUploadLogger)

internal object MultipartUploadLogger : FlexiLog() {
	/**
	 * Used to determine if we should log to the console or not.
	 */
	override fun canLogToConsole(type: LogType): Boolean = S3Uploader.canLog(type)

	/**
	 * Used to determine if we should send a report (to Crashlytics or equivalent)
	 */
	override fun shouldReport(type: LogType): Boolean = false

	override fun shouldReportException(tr: Throwable): Boolean = false

	/**
	 * Implement the actual reporting.
	 *
	 * @param type [Int] @[LogType], the type of log this came from.
	 * @param tag [Class] The Log tag
	 * @param msg [String] The Log message.
	 */
	override fun report(type: LogType, tag: String, msg: String) {
		/* No reporting from the lib */
	}

	/**
	 * Implement the actual reporting.
	 *
	 * @param type [Int] @[LogType], the type of log this came from.
	 * @param tag [Class] The Log tag
	 * @param msg [String] The Log message.
	 * @param tr  [Throwable] to be attached to the Log.
	 */
	override fun report(type: LogType, tag: String, msg: String, tr: Throwable) {
		/* No reporting from the lib */
	}
}
