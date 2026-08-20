plugins {
	`java-platform`
	`maven-publish`
}

group = "com.github.appoly.AppolyDroid-Toolbox"

// The version this BOM publishes at, and the version its constraints demand — they must be the
// same, or the BOM asks for modules that were never published.
//
// JitPack builds a branch with `-Pversion=<branch>-SNAPSHOT` and rewrites each artifact's own
// version to match, but it does NOT rewrite the versions inside this BOM's <dependencyManagement>.
// Hardcoding TOOLBOX_VERSION here therefore published a snapshot BOM demanding release modules
// (e.g. a `...-SNAPSHOT` BOM requiring BaseRepo:1.8.2), which made every branch and PR snapshot
// unresolvable through the BOM — precisely the workflow needed to verify a publishing change.
//
// Honour -Pversion when present; fall back to TOOLBOX_VERSION for local and tagged builds.
val publishedVersion: String =
	(project.findProperty("version") as? String)
		?.takeIf { it.isNotBlank() && it != "unspecified" }
		?: BuildConfig.TOOLBOX_VERSION

javaPlatform {
	allowDependencies()
}

dependencies {
	// Define constraints for all AppolyDroid modules
	constraints {
		// Core modules
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging:${publishedVersion}")

		// Core Appoly specific modules
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-AppolyJson:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging-AppolyJson:${publishedVersion}")

		// UI State modules
		api("com.github.appoly.AppolyDroid-Toolbox:UiState:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar-UiState:${publishedVersion}")

		// Date/Time modules
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Room:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization:${publishedVersion}")

		// Compose & Pagination modules
		api("com.github.appoly.AppolyDroid-Toolbox:ComposeExtensions:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:SegmentedControl:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions:${publishedVersion}")

		// S3 & Utility modules
		api("com.github.appoly.AppolyDroid-Toolbox:S3Uploader:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:S3Uploader-Multipart:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader-Multipart:${publishedVersion}")

		// Connectivity Monitor
		api("com.github.appoly.AppolyDroid-Toolbox:ConnectivityMonitor:${publishedVersion}")

		// Navigation modules
		api("com.github.appoly.AppolyDroid-Toolbox:Nav3Navigation:${publishedVersion}")

		// Mock Interceptor modules
		api("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-Serialization:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-AppolyJson:${publishedVersion}")
		api("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-Retrofit:${publishedVersion}")
	}
}

publishing {
	publications {
		create<MavenPublication>("bom") {
			from(components["javaPlatform"])
			groupId = "com.github.appoly.AppolyDroid-Toolbox"
			artifactId = "AppolyDroid-Toolbox-bom"
			version = publishedVersion
		}
	}
}