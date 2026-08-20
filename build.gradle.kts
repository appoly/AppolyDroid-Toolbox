import org.gradle.api.publish.tasks.GenerateModuleMetadata

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.kotlinKSP) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlinxSerialization) apply false
}

// Centralised Android unit-test configuration, applied to every Android library module so the
// Robolectric/Compose test setup stays consistent and new modules inherit it automatically:
//  - isIncludeAndroidResources: lets Robolectric load the merged manifest/resources on the JVM
//    unit-test classpath (Compose UI tests, in-memory Room DAO tests, etc.).
//  - isReturnDefaultValues: makes un-mocked android.* calls return defaults in plain JVM tests;
//    a no-op for Robolectric tests, which get real shadow implementations regardless.
subprojects {
	plugins.withId("com.android.library") {
		extensions.configure<com.android.build.api.dsl.LibraryExtension> {
			testOptions {
				unitTests {
					isIncludeAndroidResources = true
					isReturnDefaultValues = true
				}
			}
		}
	}
}

// Publish POM-only: no Gradle Module Metadata.
//
// JitPack rewrites the published `.module` and drops the `-sources` classifier from the sources
// variant's file name — it records `BaseRepo-<ver>.jar` where the artifact it is describing is
// actually `BaseRepo-<ver>-sources.jar` (same size, same checksums). Gradle then requests a file
// that does not exist, gives up silently, and Android Studio falls back to showing decompiled
// classes. That is why consumers have to keep clicking "Choose Sources" after every version bump.
//
// The strip is unconditional, not a side effect of coordinate rewriting: `CryptoRoomDB` declares
// coordinates that match JitPack's hosting exactly, has an unmodified `component` block, and still
// loses the classifier. So it cannot be fixed by correcting what we publish — only by not
// publishing metadata JitPack will mangle.
//
// Without a `.module`, Gradle resolves through the POM, where sources are found by the `-sources`
// classifier convention. That artifact *is* published correctly, so source navigation works.
//
// Trade-off: no variant-aware resolution for consumers. Acceptable here — every module publishes a
// single release variant, and the BOM is a `java-platform` whose constraints travel in the POM's
// `<dependencyManagement>`.
//
// Verified 2026-08-20. See issue #106 / the 1.8.2 release notes.
subprojects {
	plugins.withId("maven-publish") {
		tasks.withType<GenerateModuleMetadata>().configureEach {
			enabled = false
		}
	}
}

tasks.wrapper {
	gradleVersion = "9.7.0"
	distributionType = Wrapper.DistributionType.ALL
}

// Register the custom task to update README versions
tasks.register<UpdateReadmeVersions>("updateReadmeVersions") {
    group = "documentation"
    description = "Updates version references in README files based on toolboxVersion in libs.versions.toml"
}

// Hook into the release process
tasks.register("prepareRelease") {
    dependsOn("updateReadmeVersions")
    group = "release"
    description = "Prepares the project for release by updating documentation"
}

// This code runs during Gradle configuration phase
// It will run the updateReadmeVersions task during every Gradle sync
gradle.projectsEvaluated {
    rootProject.tasks.named("updateReadmeVersions").get().actions.forEach { action ->
        action.execute(rootProject.tasks.named("updateReadmeVersions").get())
    }
}

// The Kover aggregation (settings plugin) report tasks read coverage emitted by each module's
// unit-test run, but don't auto-depend on those test tasks. Without this wiring a standalone
// `./gradlew koverHtmlReport` (no `test` in the same invocation) reports "no coverage information
// was found". Make the root report tasks depend on every module's `test` so they always run
// against fresh coverage. CI still passes `test` explicitly; Gradle de-duplicates, so this only
// makes local standalone report runs work.
gradle.projectsEvaluated {
    val testTasks = subprojects.flatMap { sp -> sp.tasks.matching { it.name == "test" } }
    listOf("koverHtmlReport", "koverXmlReport", "koverVerify").forEach { reportName ->
        rootProject.tasks.findByName(reportName)?.dependsOn(testTasks)
    }
}

// Create a task to check if README versions are up to date
tasks.register("checkReadmeVersions") {
    group = "verification"
    description = "Checks if README version references match toolboxVersion in libs.versions.toml"
    doLast {
        // Run the version check without actual modifications
        val checkTask = tasks.named("updateReadmeVersions").get() as UpdateReadmeVersions
        checkTask.checkOnly.set(true)
        checkTask.updateVersions()
    }
}
