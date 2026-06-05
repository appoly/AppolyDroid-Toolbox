package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LogType

/**
 * No-op [FlexiLog] used in the BaseRepo-AppolyJson unit tests to keep
 * `android.util.Log` out of the call path entirely.
 */
internal object SilentTestLogger : FlexiLog() {
	override fun canLogToConsole(type: LogType): Boolean = false
	override fun shouldReport(type: LogType): Boolean = false
	override fun shouldReportException(tr: Throwable): Boolean = false
	override fun report(type: LogType, tag: String, msg: String) {}
	override fun report(type: LogType, tag: String, msg: String, tr: Throwable) {}
}
