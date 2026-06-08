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

tasks.wrapper {
	gradleVersion = "8.11.1"
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
