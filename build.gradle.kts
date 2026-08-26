import com.vanniktech.maven.publish.MavenPublishBaseExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.kotlinKSP) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlinxSerialization) apply false
	alias(libs.plugins.vanniktech.publish) apply false
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

// A signing key is supplied by scripts/publish.sh (from 1Password) and by the release workflow's
// secrets. Pull-request CI has neither, deliberately — a PR build must not hold the release key —
// yet it still runs publishToMavenLocal to feed the variant-resolution gate, which reads module
// metadata and POMs and never looks at a signature. Calling signAllPublications() unconditionally
// therefore failed that step with "no configured signatory". Signing is enabled only when a key is
// actually present; the task-graph guard below makes an unsigned Central upload impossible rather
// than merely unlikely, so the relaxation cannot leak into a real release.
val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent ||
	providers.gradleProperty("signing.keyId").isPresent

gradle.taskGraph.whenReady {
	if (!hasSigningKey && allTasks.any { it.name.contains("MavenCentral") || it.name == "releaseRepository" }) {
		throw GradleException(
			"Refusing to upload to Maven Central without a signing key: Central rejects unsigned " +
				"artifacts, and a partial upload cannot be undone. Publish with ./scripts/publish.sh, " +
				"or set ORG_GRADLE_PROJECT_signingInMemoryKey/KeyId/KeyPassword."
		)
	}
}

// Shared Maven Central publishing configuration.
//
// Replaces the JitPack-era machinery entirely: the POM-only workaround, the hand-rolled sources
// jar, and the module-metadata juggling all existed to survive JitPack rewriting what it serves.
// Central serves exactly what is uploaded, so the plugin's defaults are correct and the workarounds
// are deleted rather than ported.
//
// Everything shared lives here; a module declares only its own name and description. Central
// rejects an incomplete POM — a missing developers block is a hard rejection — so the required
// fields are set once, centrally, where they cannot be forgotten on a new module.
subprojects {
	plugins.withId("com.vanniktech.maven.publish") {
		extensions.configure<MavenPublishBaseExtension> {
			publishToMavenCentral()
			if (hasSigningKey) {
				signAllPublications()
			}

			// Artifact IDs are lowercased module names: uk.co.appoly.droid:s3uploader-multipart.
			coordinates(
				groupId = "uk.co.appoly.droid",
				artifactId = project.name.lowercase(),
				version = BuildConfig.TOOLBOX_VERSION
			)

			pom {
				url.set("https://github.com/appoly/AppolyDroid-Toolbox")
				inceptionYear.set("2024")

				licenses {
					license {
						name.set("GNU General Public License v3.0")
						url.set("https://www.gnu.org/licenses/gpl-3.0.html")
					}
				}

				developers {
					developer {
						id.set("appoly")
						name.set("Appoly")
						email.set("android@appoly.co.uk")
						url.set("https://github.com/appoly")
					}
				}

				scm {
					url.set("https://github.com/appoly/AppolyDroid-Toolbox")
					connection.set("scm:git:git://github.com/appoly/AppolyDroid-Toolbox.git")
					developerConnection.set("scm:git:ssh://git@github.com/appoly/AppolyDroid-Toolbox.git")
				}
			}
		}
	}
}

tasks.wrapper {
	gradleVersion = "9.7.1"
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
