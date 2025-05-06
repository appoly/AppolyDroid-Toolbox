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
include(":app")
include(":BaseRepo")
include(":BaseRepo-S3Uploader")
include(":BaseRepo-Paging")
include(":UiState")
include(":DateHelperUtil")
include(":DateHelperUtil-Room")
include(":DateHelperUtil-Serialization")
include(":DateHelperUtil-MP")
include(":DateHelperUtil-MP-Room")
include(":DateHelperUtil-MP-Serialization")
