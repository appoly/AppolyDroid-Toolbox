// Standalone build, deliberately NOT included in the main one.
//
// The library build sets RepositoriesMode.FAIL_ON_PROJECT_REPOS, and adding mavenLocal() to its
// settings would let stale locally-published artifacts shadow real dependencies for every module.
// This probe needs mavenLocal to read what the toolbox just published, so it lives in its own build
// with its own resolution rules and cannot affect how the library itself resolves anything.
dependencyResolutionManagement {
	repositories {
		mavenLocal()
		google()
		mavenCentral()
		maven { url = uri("https://jitpack.io") }
	}
}

rootProject.name = "publishing-check"
