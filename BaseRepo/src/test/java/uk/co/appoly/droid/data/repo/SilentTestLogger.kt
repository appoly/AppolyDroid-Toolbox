package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LogType

/**
 * No-op [FlexiLog] used in the BaseRepo unit tests to suppress all logging.
 *
 * [GenericBaseRepo] reconfigures the internal `BaseRepoLog` with whatever logger
 * is passed to its constructor. Passing this logger keeps `android.util.Log`
 * out of the call path entirely (it only fires when [canLogToConsole] returns
 * true), which matters in plain JVM unit tests where the Android stubs would
 * otherwise throw.
 */
internal object SilentTestLogger : FlexiLog() {
	override fun canLogToConsole(type: LogType): Boolean = false
	override fun shouldReport(type: LogType): Boolean = false
	override fun shouldReportException(tr: Throwable): Boolean = false
	override fun report(type: LogType, tag: String, msg: String) {}
	override fun report(type: LogType, tag: String, msg: String, tr: Throwable) {}
}
