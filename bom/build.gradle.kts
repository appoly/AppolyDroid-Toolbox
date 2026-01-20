plugins {
	`java-platform`
	`maven-publish`
}

group = "com.github.appoly"

javaPlatform {
	allowDependencies()
}

dependencies {
	// Define constraints for all AppolyDroid modules
	constraints {
		// Core modules
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging:${BuildConfig.TOOLBOX_VERSION}")

		// Core Appoly specific modules
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-AppolyJson:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging-AppolyJson:${BuildConfig.TOOLBOX_VERSION}")

		// UI State modules
		api("com.github.appoly.AppolyDroid-Toolbox:UiState:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar-UiState:${BuildConfig.TOOLBOX_VERSION}")

		// Date/Time modules
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Room:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization:${BuildConfig.TOOLBOX_VERSION}")

		// Compose & Pagination modules
		api("com.github.appoly.AppolyDroid-Toolbox:ComposeExtensions:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions:${BuildConfig.TOOLBOX_VERSION}")

		// S3 & Utility modules
		api("com.github.appoly.AppolyDroid-Toolbox:S3Uploader:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:S3Uploader-Multipart:${BuildConfig.TOOLBOX_VERSION}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader-Multipart:${BuildConfig.TOOLBOX_VERSION}")

		// Connectivity Monitor
		api("com.github.appoly.AppolyDroid-Toolbox:ConnectivityMonitor:${BuildConfig.TOOLBOX_VERSION}")
	}
}

publishing {
	publications {
		create<MavenPublication>("bom") {
			from(components["javaPlatform"])
			groupId = "com.github.appoly"
			artifactId = "AppolyDroid-Toolbox-bom"
			version = BuildConfig.TOOLBOX_VERSION
		}
	}
}