// Standalone build, deliberately NOT included in the main one.
//
// The library build sets RepositoriesMode.FAIL_ON_PROJECT_REPOS, and adding mavenLocal() to its
// settings would let stale locally-published artifacts shadow real dependencies for every module.
// This probe needs mavenLocal to read what the toolbox just published, so it lives in its own build
// with its own resolution rules and cannot affect how the library itself resolves anything.
dependencyResolutionManagement {
	repositories {
		// mavenLocal FIRST and no JitPack repository, deliberately. The point of this check is what
		// the toolbox *just published locally*. With JitPack in the list, a module that silently
		// failed to publish could resolve from the released artifact of the same version instead
		// and the check would pass having tested the wrong thing entirely.
		mavenLocal()
		google()
		mavenCentral()
	}
}

rootProject.name = "publishing-check"
