plugins {
	`java-platform`
	alias(libs.plugins.vanniktech.publish)
}

// Constraints pin every toolbox module to a single version, so a consumer importing this platform
// gets a coherent set without naming versions itself.
//
// The version is simply TOOLBOX_VERSION. The previous `-Pversion` juggling existed only because
// JitPack rewrote each artifact's version to the branch or tag being built while leaving these
// constraints untouched, which made snapshot BOMs demand release modules. Maven Central publishes
// what it is given, so the workaround is gone.

javaPlatform {
	allowDependencies()
}

dependencies {
	constraints {
		// Core modules
		api("uk.co.appoly.droid:baserepo:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:baserepo-s3uploader:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:baserepo-paging:${BuildConfig.TOOLBOX_VERSION}")

		// Core Appoly specific modules
		api("uk.co.appoly.droid:baserepo-appolyjson:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:baserepo-paging-appolyjson:${BuildConfig.TOOLBOX_VERSION}")

		// UI State modules
		api("uk.co.appoly.droid:uistate:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:appsnackbar:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:appsnackbar-uistate:${BuildConfig.TOOLBOX_VERSION}")

		// Date/Time modules
		api("uk.co.appoly.droid:datehelperutil:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:datehelperutil-room:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:datehelperutil-serialization:${BuildConfig.TOOLBOX_VERSION}")

		// Compose & Pagination modules
		api("uk.co.appoly.droid:composeextensions:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:segmentedcontrol:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:lazylistpagingextensions:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:lazygridpagingextensions:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:pagingextensions:${BuildConfig.TOOLBOX_VERSION}")

		// S3 & Utility modules
		api("uk.co.appoly.droid:s3uploader:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:s3uploader-multipart:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:baserepo-s3uploader-multipart:${BuildConfig.TOOLBOX_VERSION}")

		// Connectivity Monitor
		api("uk.co.appoly.droid:connectivitymonitor:${BuildConfig.TOOLBOX_VERSION}")

		// Navigation modules
		api("uk.co.appoly.droid:nav3navigation:${BuildConfig.TOOLBOX_VERSION}")

		// Mock Interceptor modules
		api("uk.co.appoly.droid:mockinterceptor:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:mockinterceptor-serialization:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:mockinterceptor-appolyjson:${BuildConfig.TOOLBOX_VERSION}")
		api("uk.co.appoly.droid:mockinterceptor-retrofit:${BuildConfig.TOOLBOX_VERSION}")
	}
}

mavenPublishing {
	pom {
		name.set("AppolyDroid Toolbox BOM")
		description.set("Bill of Materials pinning a coherent set of AppolyDroid Toolbox module versions.")
	}
}
