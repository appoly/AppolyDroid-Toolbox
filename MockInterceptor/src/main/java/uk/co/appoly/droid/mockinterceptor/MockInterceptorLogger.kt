package uk.co.appoly.droid.mockinterceptor

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LogType
import com.duck.flexilogger.LoggerWithLevel
import com.duck.flexilogger.LoggingLevel

/**
 * Internal [LoggerWithLevel] instance used by MockInterceptor for debug logging.
 *
 * The logger and level can be overridden via [MockApiInterceptor] constructor parameters.
 */
val MockInterceptorLog = LoggerWithLevel(LoggingLevel.V, MockInterceptorLogger)

/** Default no-op [FlexiLog] implementation used when no custom logger is provided. */
internal object MockInterceptorLogger : FlexiLog() {
	override fun canLogToConsole(type: LogType): Boolean = true

	override fun shouldReport(type: LogType): Boolean = false

	override fun shouldReportException(tr: Throwable): Boolean = false

	override fun report(type: LogType, tag: String, msg: String) {
		/* No reporting from the lib */
	}

	override fun report(type: LogType, tag: String, msg: String, tr: Throwable) {
		/* No reporting from the lib */
	}
}
