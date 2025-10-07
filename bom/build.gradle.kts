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
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging:${libs.versions.toolboxVersion.get()}")

		// UI State modules
		api("com.github.appoly.AppolyDroid-Toolbox:UiState:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar-UiState:${libs.versions.toolboxVersion.get()}")

		// Date/Time modules
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Room:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization:${libs.versions.toolboxVersion.get()}")

		// Compose & Pagination modules
		api("com.github.appoly.AppolyDroid-Toolbox:ComposeExtensions:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:${libs.versions.toolboxVersion.get()}")
		api("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions:${libs.versions.toolboxVersion.get()}")

		// S3 & Utility modules
		api("com.github.appoly.AppolyDroid-Toolbox:S3Uploader:${libs.versions.toolboxVersion.get()}")

		// Connectivity Monitor
		api("com.github.appoly.AppolyDroid-Toolbox:ConnectivityMonitor:${libs.versions.toolboxVersion.get()}")
	}
}

publishing {
	publications {
		create<MavenPublication>("bom") {
			from(components["javaPlatform"])
			groupId = "com.github.appoly"
			artifactId = "AppolyDroid-Toolbox-bom"
			version = libs.versions.toolboxVersion.get()
		}
	}
}