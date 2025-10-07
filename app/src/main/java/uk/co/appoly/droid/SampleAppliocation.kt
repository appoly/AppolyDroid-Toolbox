package uk.co.appoly.droid

import com.duck.flexilogger.LoggingLevel

class SampleApplication: ConnectivityMonitorApplication() {

    init {
        setLogger(
            logger = Log,
            loggingLevel = LoggingLevel.V,
        )
    }
}