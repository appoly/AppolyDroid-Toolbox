package uk.co.appoly.droid.util

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LogType

/**
 * Test [FlexiLog] that captures every log call into [entries] for assertion.
 *
 * - `canLogToConsole(...) = false` keeps `android.util.Log` out of the call path in JVM tests
 * - `shouldReport(...) = true` routes every log through [report], where we record it
 *
 * Use this in place of [SilentTestLogger] when a test needs to assert *what* was logged
 * (e.g. log-level or log-message attribution), not just that nothing crashed.
 */
internal class RecordingTestLogger : FlexiLog() {

	/** A captured log call. */
	data class Entry(
		val type: LogType,
		val tag: String,
		val msg: String,
		val throwable: Throwable?
	)

	val entries: MutableList<Entry> = mutableListOf()

	/** Filter helper: returns entries with the given [LogType]. */
	fun ofType(type: LogType): List<Entry> = entries.filter { it.type == type }

	fun reset() = entries.clear()

	override fun canLogToConsole(type: LogType): Boolean = false
	override fun shouldReport(type: LogType): Boolean = true
	override fun shouldReportException(tr: Throwable): Boolean = true

	override fun report(type: LogType, tag: String, msg: String) {
		entries.add(Entry(type, tag, msg, null))
	}

	override fun report(type: LogType, tag: String, msg: String, tr: Throwable) {
		entries.add(Entry(type, tag, msg, tr))
	}
}
