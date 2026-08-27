plugins {
	id("jvm-ecosystem")
}

import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment

// ============================================================================
// Consumer-shaped publishing verification
// ============================================================================
//
// The 1.8.2 duplicate-class regression was invisible to the toolbox's own build: the library
// compiled and published perfectly, and only an Android *consumer* resolving the published metadata
// could see the problem. It was also invisible to most consumers — one was green on identical
// artifacts because it does not depend on MockInterceptor, and another's release flavour built fine
// because it only pulls MockInterceptor into a debug-side flavour. A defect visible only to
// consumers who happen to pull one particular module should not be discovered by consumers.
//
// So: resolve the published modules the way an Android app would, and assert the outcome.
//
// Scope, stated honestly. This probes the JVM-published modules only, which is where this class of
// defect lives: a Kotlin/JVM module resolves the `-jvm` variant of a multiplatform dependency at
// publish time, and a POM-only publish then hard-codes it, so an Android consumer receives
// `okhttp-jvm` beside the `okhttp-android` it had already correctly resolved. Android-published
// modules cannot be resolved into a plain JVM configuration without artifact-transform plumbing,
// and their equivalent pin (`ComposeExtensions` -> `kotlinx-coroutines-android`) is harmless for
// Android consumers.
//
// Assertions are paired on purpose. "No `-jvm` artifact present" is indistinguishable from "nothing
// resolved at all", so every absence check sits beside a presence check and a resolution floor.
// That is not hypothetical: during the 1.8.3 verification a consumer-side script reported a clean
// classpath while 17 nodes had silently failed, because `gradle dependencies` exits 0 regardless.
// A check that can pass vacuously is worse than no check.

val toolboxVersion: String = (findProperty("toolboxVersion") as String?)
	?: Regex("""TOOLBOX_VERSION\s*=\s*"([^"]+)"""")
		.find(file("../buildSrc/src/main/kotlin/BuildConfig.kt").readText())
		?.groupValues?.get(1)
	?: error("Could not determine TOOLBOX_VERSION; pass -PtoolboxVersion=x.y.z")

val jvmPublishedModules = listOf(
	"mockinterceptor",
	"mockinterceptor-serialization",
	"mockinterceptor-appolyjson",
	"mockinterceptor-retrofit",
)

// A configuration shaped like an Android app's runtime classpath.
val androidConsumerProbe: Configuration by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
	attributes {
		attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
		attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
		attribute(
			TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
			objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID)
		)
	}
}

dependencies {
	jvmPublishedModules.forEach { module ->
		androidConsumerProbe("uk.co.appoly.droid:$module:$toolboxVersion")
	}
}

tasks.register("verifyPublishedVariantResolution") {
	group = "verification"
	description = "Resolves published toolbox modules as an Android consumer; fails on platform-variant duplicates"

	doLast {
		logger.lifecycle("Probing published toolbox $toolboxVersion as an Android consumer")

		val result = androidConsumerProbe.incoming.resolutionResult

		// Report unresolved dependencies with their reasons rather than letting them show up
		// downstream as a mysteriously small graph.
		val unresolved = result.root.dependencies.filterIsInstance<UnresolvedDependencyResult>()
		if (unresolved.isNotEmpty()) {
			throw GradleException(
				"${unresolved.size} dependency/ies could not be resolved as an Android consumer. That is " +
					"itself a failure mode under test: published metadata a consumer cannot use.\n" +
					"Run `./gradlew publishToMavenLocal` in the main build first if the artifacts are absent.\n" +
					unresolved.joinToString("\n") { "  - ${it.requested.displayName}: ${it.failure.message}" }
			)
		}

		val resolved = result.allComponents.mapNotNull { it.moduleVersion }

		val names = resolved.map { it.name }.toSet()
		val failures = mutableListOf<String>()
		val notes = mutableListOf<String>()

		// --- floors: prove the machinery ran, so the absences below mean something ---
		val toolbox = resolved.filter { it.group == "uk.co.appoly.droid" }
		if (toolbox.size < jvmPublishedModules.size) {
			failures += "only ${toolbox.size} of ${jvmPublishedModules.size} probed toolbox modules resolved " +
				"(${toolbox.map { it.name }.sorted()}) — absence assertions would be vacuous"
		} else {
			notes += "${toolbox.size} toolbox modules resolved at $toolboxVersion"
		}
		if (resolved.size < 10) {
			failures += "only ${resolved.size} components resolved in total — the graph looks truncated"
		} else {
			notes += "${resolved.size} components on the probe classpath"
		}
		resolved.filter { it.group == "uk.co.appoly.droid" && it.version != toolboxVersion }
			.takeIf { it.isNotEmpty() }
			?.let { failures += "toolbox modules at unexpected versions: ${it.map { m -> "${m.name}:${m.version}" }.sorted()}" }

		// --- the regression itself: a platform artifact beside its counterpart ---
		names.filter { it.endsWith("-jvm") && names.contains(it.removeSuffix("-jvm") + "-android") }
			.sorted()
			.takeIf { it.isNotEmpty() }
			?.let { dupes ->
				failures += dupes.joinToString(", ", prefix = "platform-variant duplicates on an Android classpath: ") {
					"$it + ${it.removeSuffix("-jvm")}-android"
				}
			}

		// --- paired presence/absence for the two that actually broke a consumer ---
		listOf("okhttp", "flexilogger").forEach { base ->
			val jvm = names.contains("$base-jvm")
			val androidOrRoot = names.contains("$base-android") || names.contains(base)
			when {
				jvm && androidOrRoot -> failures += "$base resolved as both $base-jvm and its android/root variant"
				jvm -> failures += "$base resolved as $base-jvm on an Android classpath"
				!androidOrRoot -> failures += "$base did not resolve at all — absence of $base-jvm proves nothing"
				else -> notes += "$base resolved to its android/root variant, no -jvm present"
			}
		}

		notes.forEach { logger.lifecycle("  PASS  $it") }
		failures.forEach { logger.lifecycle("  FAIL  $it") }

		if (failures.isNotEmpty()) {
			throw GradleException(
				"Published metadata would break an Android consumer (${failures.size} assertion(s) failed):\n" +
					failures.joinToString("\n") { "  - $it" } +
					"\n\nThis is the shape of the 1.8.2 regression. Check that Gradle Module Metadata is " +
					"still being published — see the publishing comment in the root build.gradle.kts."
			)
		}
		logger.lifecycle("OK: published toolbox resolves cleanly as an Android consumer.")
	}
}
