import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

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

// Publish Gradle Module Metadata, but keep the sources jar out of it.
//
// 1.8.2 published POM-only to stop JitPack mangling the sources variant (see 24f40ef for that
// story). It fixed source navigation and broke Android consumers. Without a `.module`, a module's
// POM records the *resolved* platform artifact of each multiplatform dependency instead of the
// variant-aware root coordinate: `MockInterceptor` recorded `okhttp-jvm` and `flexilogger-jvm`, so
// consuming it from an Android app dragged those onto a classpath that had already correctly
// resolved the `-android` artifacts — 13 duplicate `okhttp3.internal.ws.*` classes and a failed
// `checkDuplicateClasses`. Consumer-side excludes are unbounded whack-a-mole, since every
// multiplatform dependency reachable through a JVM-published module has the same shape.
//
// So the metadata has to come back. The sources problem is avoided differently: never give JitPack
// a sources *variant* to mangle. No module calls `withSourcesJar()` — that would register one in
// GMM. Each publication instead attaches a plain `-sources` classifier artifact, which Gradle finds
// through the POM classifier convention, the same route 24f40ef proved works.
//
// The load-bearing assumption is that Gradle falls back to the classifier when GMM carries no
// sources variant. That CANNOT be verified locally: mavenLocal never reproduces JitPack's
// rewriting, so publishing here looks perfect either way. It needs a real JitPack build of a branch
// snapshot or pre-release tag, checking *source navigation* — not merely that consumers compile.
//
// If that check fails, the fallback is reverting to GMM-with-sources-variant (the 1.8.1 shape):
// builds work for everyone, source navigation needs "Choose Sources". Broken navigation is an
// annoyance; a classpath that will not assemble is a hard block.
subprojects {
	plugins.withId("maven-publish") {
		afterEvaluate {
			// The BOM is a java-platform: no sources exist to publish.
			if (plugins.hasPlugin("java-platform")) return@afterEvaluate

			val sourcesJar = tasks.register<Jar>("toolboxSourcesJar") {
				archiveClassifier.set("sources")
				// Conventional layout; this repo keeps Kotlin under src/main/java. Directories that
				// do not exist simply contribute nothing.
				from("src/main/java", "src/main/kotlin")
			}

			extensions.configure<PublishingExtension> {
				publications.withType<MavenPublication>().configureEach {
					artifact(sourcesJar)
				}
			}
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
