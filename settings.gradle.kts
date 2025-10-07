pluginManagement {
	repositories {
		google {
			content {
				includeGroupByRegex("com\\.android.*")
				includeGroupByRegex("com\\.google.*")
				includeGroupByRegex("androidx.*")
			}
		}
		mavenCentral()
		gradlePluginPortal()
	}
}
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
		maven {
			url = uri("https://jitpack.io")
		}
	}
}

rootProject.name = "AppolyDroid"
include(":bom")
include(":app")
include(":BaseRepo")
include(":BaseRepo-S3Uploader")
include(":BaseRepo-Paging")
include(":UiState")
include(":DateHelperUtil")
include(":DateHelperUtil-Room")
include(":DateHelperUtil-Serialization")
include(":AppSnackBar")
include(":AppSnackBar-UiState")
include(":LazyListPagingExtensions")
include(":LazyGridPagingExtensions")
include(":PagingExtensions")
include(":S3Uploader")
include(":ComposeExtensions")
include(":ConnectivityMonitor")
