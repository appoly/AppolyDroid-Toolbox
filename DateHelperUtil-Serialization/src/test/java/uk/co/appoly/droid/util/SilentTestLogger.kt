package uk.co.appoly.droid.util

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LogType

/**
 * No-op [FlexiLog] used in unit tests to suppress all logging. Routing through
 * [DateHelper.setLogger] with this logger keeps `android.util.Log` out of the
 * call path entirely (it only fires when [canLogToConsole] returns true), which
 * matters in JVM unit tests where the Android stubs otherwise throw.
 */
internal object SilentTestLogger : FlexiLog() {
	override fun canLogToConsole(type: LogType): Boolean = false
	override fun shouldReport(type: LogType): Boolean = false
	override fun shouldReportException(tr: Throwable): Boolean = false
	override fun report(type: LogType, tag: String, msg: String) {}
	override fun report(type: LogType, tag: String, msg: String, tr: Throwable) {}
}
