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

// Kover coverage aggregation across the whole build (Android + pure-JVM modules) into a
// single root report. Version kept in sync with `kover` in gradle/libs.versions.toml (the
// settings plugins block can't read the version catalog).
plugins {
	id("org.jetbrains.kotlinx.kover.aggregation") version "0.9.8"
}

kover {
	enableCoverage()
	reports {
		// The demo app is scaffolding — measure the published libraries only.
		excludedProjects.add(":app")
		// Coverage floor for the gate. Measured aggregate is ~48% (Pass 2); 45 leaves a
		// little headroom. Raise as the remaining integration core (MultipartUploadManager,
		// WorkManager workers, Retrofit/Sandwich network layer, ConnectivityMonitorApplication)
		// gets covered.
		verify {
			rule {
				bound {
					minValue = 50
				}
			}
		}
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
include(":S3Uploader-Multipart")
include(":BaseRepo-S3Uploader-Multipart")
include(":ComposeExtensions")
include(":ConnectivityMonitor")
include(":BaseRepo-AppolyJson")
include(":BaseRepo-Paging-AppolyJson")
include(":MockInterceptor")
include(":MockInterceptor-Serialization")
include(":MockInterceptor-AppolyJson")
include(":MockInterceptor-Retrofit")
include(":SegmentedControl")
