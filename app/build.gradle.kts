import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlinxSerialization)
	alias(libs.plugins.kotlinKSP)
}

configure<ApplicationExtension> {
	namespace = "uk.co.appoly.droid.app"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	defaultConfig {
		applicationId = "uk.co.appoly.droid"
		minSdk = BuildConfig.MinSdk.max() // App uses highest minSdk of all modules
		targetSdk = BuildConfig.Sdk.TARGET
		versionCode = 1
		versionName = BuildConfig.TOOLBOX_VERSION

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			// Minified so the `verifyConsumerKeepRules` task (below) can confirm the library
			// modules' consumer R8 rules survive shrinking of a real consuming app.
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

// Coverage is aggregated across the whole build by the kover.aggregation settings plugin
// (see settings.gradle.kts) — no per-project Kover config needed here. The demo app is
// excluded there via excludedProjects.

dependencies {

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime)
	implementation(libs.androidx.activity.compose)

	//Compose
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.androidx.ui.tooling.preview)
	implementation(libs.androidx.material3)
	implementation(libs.compose.material.icons.extended)

	// Navigation is provided by :Nav3Navigation (androidx Navigation 3)

	//AppolyDroidBaseRepo
	implementation(project(":BaseRepo"))
	implementation(project(":BaseRepo-AppolyJson"))
	implementation(project(":BaseRepo-S3Uploader"))
	implementation(project(":BaseRepo-Paging"))
	implementation(project(":BaseRepo-Paging-AppolyJson"))
	implementation(project(":DateHelperUtil"))
	implementation(project(":DateHelperUtil-Room"))
	implementation(project(":DateHelperUtil-Serialization"))

	//Room (for the DateHelperUtil-Room demo)
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)
	implementation(project(":UiState"))
	implementation(project(":AppSnackBar"))
	implementation(project(":AppSnackBar-UiState"))
	implementation(project(":SegmentedControl"))
	implementation(project(":ComposeExtensions"))
	implementation(project(":PagingExtensions"))
	implementation(project(":LazyListPagingExtensions"))
	implementation(project(":LazyGridPagingExtensions"))
	implementation(project(":S3Uploader"))
	implementation(project(":S3Uploader-Multipart"))
	implementation(project(":ConnectivityMonitor"))
	implementation(project(":Nav3Navigation"))
	implementation(project(":MockInterceptor"))
	implementation(project(":MockInterceptor-Serialization"))
	implementation(project(":MockInterceptor-AppolyJson"))
	implementation(project(":MockInterceptor-Retrofit"))

	// For test backend API
	implementation(libs.retrofit)
	implementation(libs.retrofit.serializationConverter)
	implementation(libs.okhttp)
	implementation(libs.okhttp.logging)
	implementation(libs.kotlinx.serialization.json)
	implementation(platform(libs.sandwich.bom))
	implementation(libs.sandwich)
	implementation(libs.sandwich.retrofit)

	//Paging
	implementation(libs.paging.runtime)
	implementation(libs.paging.compose)
	testImplementation(libs.paging.common)

	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.ui.test.junit4)
	debugImplementation(libs.androidx.ui.tooling)
	debugImplementation(libs.androidx.ui.test.manifest)
}

// ---------------------------------------------------------------------------------------------
// verifyConsumerKeepRules
//
// Device-free guard that the AppolyDroid modules' consumer R8 rules actually survive minification
// of a real consuming app. This demo app depends on every module and is minified (see buildTypes
// above), so R8 applies all of their consumer-rules.pro. R8 writes `seeds.txt` — the exact set of
// classes/members its keep rules matched — and this task asserts every serializer / converter the
// consumer rules are supposed to protect is in it. If a module's consumer rule regresses, the
// class drops out of seeds.txt and this task fails.
//
// Reads seeds.txt (~300 KB) rather than mapping.txt (~58 MB): seeds.txt lists keep-rule matches
// directly, so presence there is a positive proof the consumer rule fired — not just that the
// class happened to survive via some other reachability path.
//
// What seeds.txt can and cannot prove
// -----------------------------------
// seeds.txt records *that* a class/member was kept, never *which* rule kept it, and it lists a
// class that R8 is still free to rename. Two consequences, both measured on this project rather
// than assumed (knock out a rule, run `:app:minifyReleaseWithR8 --rerun`, see if this task fails):
//
//   * Members duplicated by another library's shipped rules cannot be attributed here. Nav3's
//     Companion / serializer() / INSTANCE keeps are near-verbatim duplicates of the rules in
//     kotlinx-serialization-core's own `kotlinx-serialization-common.pro` (keyed on
//     @Serializable rather than `implements Nav3Screen`, which for a persisted NavKey is the
//     same set of classes). Delete Nav3's copies and seeds.txt is unchanged. They are kept as
//     insurance against that upstream file changing — not because this task verifies them.
//   * Name preservation is invisible to seeds.txt. kotlinx-serialization keeps @Serializable
//     classes with `allowobfuscation`, so a Nav3Screen appears in seeds.txt *even when renamed*.
//     Nav3 resolves each back-stack key's KSerializer by serial name, which defaults to the
//     FQCN, so a rename silently breaks restore after process death. That is what
//     `nav3NameCriticalScreens` below checks against mapping.txt — and it is the only check that
//     catches a regression in `-keep class * implements Nav3Screen`.
//
// Runs in CI (no device/emulator needed). The instrumented equivalent was abandoned because
// minifying the test APK strips the test runner's own dependencies — plumbing unrelated to the
// library. See README "R8 / ProGuard".
// ---------------------------------------------------------------------------------------------
val consumerKeepSentinels = listOf(
	// BaseRepo-Paging — generic paged wrapper
	"uk.co.appoly.droid.data.remote.model.response.PageData\$\$serializer",
	// BaseRepo-AppolyJson — standardized response envelopes
	"uk.co.appoly.droid.data.remote.model.response.GenericResponse\$\$serializer",
	"uk.co.appoly.droid.data.remote.model.response.BaseResponse\$\$serializer",
	"uk.co.appoly.droid.data.remote.model.response.ErrorBody\$\$serializer",
	// BaseRepo-Paging-AppolyJson — nested paged response
	"uk.co.appoly.droid.data.remote.model.response.GenericNestedPagedResponse\$\$serializer",
	"uk.co.appoly.droid.data.remote.model.response.NestedPageData\$\$serializer",
	// S3Uploader — pre-signed URL models + custom serializer
	"uk.co.appoly.droid.s3upload.network.GetPreSignedUrlResponse\$\$serializer",
	"uk.co.appoly.droid.s3upload.network.PreSignedURLData\$\$serializer",
	"uk.co.appoly.droid.s3upload.network.GetPreSignedUrlBody\$\$serializer",
	"uk.co.appoly.droid.s3upload.network.ErrorBody\$\$serializer",
	"uk.co.appoly.droid.s3upload.utils.StringOrListSerialiser",
	// S3Uploader-Multipart — config model, custom serializer, Room converters
	"uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints\$\$serializer",
	"uk.co.appoly.droid.s3upload.multipart.network.model.EmptyArrayAsEmptyMapSerializer",
	"uk.co.appoly.droid.s3upload.multipart.database.converter.UploadStatusConverters",
	// DateHelperUtil-Serialization — date KSerializer singletons
	"uk.co.appoly.droid.util.LocalDateSerializer",
	"uk.co.appoly.droid.util.NullableLocalDateSerializer",
	"uk.co.appoly.droid.util.DateTimeSerializer",
	"uk.co.appoly.droid.util.NullableDateTimeSerializer",
	"uk.co.appoly.droid.util.ZonedDateTimeSerializer",
	"uk.co.appoly.droid.util.NullableZonedDateTimeSerializer",
	"uk.co.appoly.droid.util.InstantSerializer",
	"uk.co.appoly.droid.util.NullableInstantSerializer",
	// BaseRepo — enum-serializer base classes consumers subclass
	"uk.co.appoly.droid.util.EnumAsStringSerializer",
	"uk.co.appoly.droid.util.NullableEnumAsStringSerializer",
	"uk.co.appoly.droid.util.EnumAsIntSerializer",
	"uk.co.appoly.droid.util.NullableEnumAsIntSerializer",
	// DateHelperUtil-Room — Room TypeConverter carrier
	"uk.co.appoly.droid.util.DBDateConverters",
	// ComposeExtensions — Serializable MutableState holders (writeObject/readObject kept for
	// process-death restore); demo app uses them via ComposeExtensionsDemoScreen so R8 retains them.
	"uk.co.appoly.droid.compose.extensions.SerializableMutableState",
	"uk.co.appoly.droid.compose.extensions.TransientMutableState",
	// Nav3Navigation — these are the CONSUMING app's own screens, not library internals. Nav3
	// persists the back stack by resolving each key's KSerializer reflectively, so the module's
	// consumer rules keep every `Nav3Screen` implementor.
	//
	// Of the five rule branches in Nav3Navigation/consumer-rules.pro, seeds.txt can only verify
	// the $$serializer keep (see "What seeds.txt can and cannot prove" above). Member sentinels
	// are asserted as exact "FQN: <member>" lines so a class-level keep cannot satisfy them —
	// they pin the members Nav3 restore actually needs, whichever rule delivers them.
	//   -keep,includedescriptorclasses class <1>$$serializer { *** INSTANCE; <methods>; }
	//   — verified: removing this branch fails this task.
	"uk.co.appoly.droid.ui.screens.TabsRoomDetailScreen\$\$serializer",
	"uk.co.appoly.droid.ui.screens.TabsRoomDetailScreen\$\$serializer: uk.co.appoly.droid.ui.screens.TabsRoomDetailScreen\$\$serializer INSTANCE",
	"uk.co.appoly.droid.ui.screens.Nav3StackProbeScreen\$\$serializer",
	//   Companion / serializer() / INSTANCE — duplicated by kotlinx-serialization's own rules,
	//   so these pin the members without attributing them to Nav3's branches.
	"uk.co.appoly.droid.ui.screens.TabsRoomDetailScreen: uk.co.appoly.droid.ui.screens.TabsRoomDetailScreen\$Companion Companion",
	"uk.co.appoly.droid.ui.screens.TabsRoomDetailScreen\$Companion: kotlinx.serialization.KSerializer serializer()",
	"uk.co.appoly.droid.ui.screens.TabsDemoScreen: uk.co.appoly.droid.ui.screens.TabsDemoScreen INSTANCE",
	"uk.co.appoly.droid.ui.screens.TabsDemoScreen: kotlinx.serialization.KSerializer serializer()",
)

// Nav3Screen implementors whose fully-qualified name must survive R8 *unrenamed*: Nav3 persists
// each back-stack key by serial name, which defaults to the FQCN. Guards
// `-keep class * implements uk.co.appoly.droid.nav3.Nav3Screen` — the one Nav3 branch that is
// load-bearing and invisible to seeds.txt, because kotlinx-serialization keeps @Serializable
// classes with `allowobfuscation` and a renamed class still appears there.
val nav3NameCriticalScreens = listOf(
	// data object screen, data class screen, and the parameterised stack probe.
	"uk.co.appoly.droid.ui.screens.TabsDemoScreen",
	"uk.co.appoly.droid.ui.screens.TabsRoomDetailScreen",
	"uk.co.appoly.droid.ui.screens.Nav3StackProbeScreen",
)

tasks.register("verifyConsumerKeepRules") {
	group = "verification"
	description = "Asserts AppolyDroid modules' consumer R8 keep rules survive minification (reads release seeds.txt + mapping.txt)."
	dependsOn("minifyReleaseWithR8")

	val seedsFile = layout.buildDirectory.file("outputs/mapping/release/seeds.txt")
	// Plain-text mapping only exists in intermediates — outputs/ carries AGP 9's binary mapping.prt.
	val mappingFile = layout.buildDirectory.file("intermediates/mapping/release/minifyReleaseWithR8/mapping.txt")
	inputs.file(seedsFile)
	inputs.file(mappingFile)
	// Re-run if the expected sets change.
	inputs.property("sentinels", consumerKeepSentinels)
	inputs.property("nameCritical", nav3NameCriticalScreens)

	doLast {
		val file = seedsFile.get().asFile
		if (!file.exists()) {
			throw GradleException(
				"seeds.txt not found at ${file.path}. Expected R8 to produce it during " +
					"minifyReleaseWithR8 — is the release build still minified (isMinifyEnabled = true)?"
			)
		}
		// seeds.txt lists each kept class as a bare FQN line, and kept members as "FQN: <member>".
		// Sentinels use whichever form matches the rule branch under test:
		//   "com.foo.Bar"                  → class-level: the bare line, or any member of it.
		//   "com.foo.Bar: com.foo.Baz qux" → member-level: that exact line and nothing else.
		// The distinction is load-bearing. A class-level keep (e.g. Nav3 rule A) emits the bare
		// line for every matching class, which would otherwise satisfy sentinels aimed at member
		// keeps and hide a regression in them.
		val seedLines = file.readLines().toHashSet()
		fun isKept(sentinel: String): Boolean = when {
			sentinel.contains(": ") -> seedLines.contains(sentinel)
			else -> seedLines.contains(sentinel) || seedLines.any { it.startsWith("$sentinel:") }
		}

		val missing = consumerKeepSentinels.filterNot(::isKept)
		if (missing.isNotEmpty()) {
			throw GradleException(
				buildString {
					appendLine("Consumer R8 keep-rule verification FAILED.")
					appendLine("These library classes were NOT kept by their consumer rules in the minified app:")
					missing.forEach { appendLine("  - $it") }
					appendLine("Fix the corresponding module's consumer-rules.pro, then re-run :app:verifyConsumerKeepRules.")
				}
			)
		}
		// --- name preservation (Nav3 serial names default to the FQCN) ---
		val mapping = mappingFile.get().asFile
		if (!mapping.exists()) {
			throw GradleException(
				"mapping.txt not found at ${mapping.path}. AGP may have moved the plain-text mapping " +
					"output — locate it and update this task, do not silently skip the name check."
			)
		}
		// Class lines are unindented and look like "from.Name -> to.Name:"; members are indented.
		val renames = mutableMapOf<String, String>()
		val nameCritical = nav3NameCriticalScreens.toHashSet()
		mapping.useLines { lines ->
			for (line in lines) {
				if (line.isEmpty() || line[0].isWhitespace() || !line.endsWith(":")) continue
				val arrow = line.indexOf(" -> ")
				if (arrow < 0) continue
				val from = line.substring(0, arrow)
				if (from !in nameCritical) continue
				renames[from] = line.substring(arrow + 4).dropLast(1)
			}
		}

		val absent = nav3NameCriticalScreens.filterNot(renames::containsKey)
		val renamed = renames.filter { (from, to) -> from != to }
		if (absent.isNotEmpty() || renamed.isNotEmpty()) {
			throw GradleException(
				buildString {
					appendLine("Consumer R8 keep-rule verification FAILED (Nav3Screen name preservation).")
					if (renamed.isNotEmpty()) {
						appendLine("These Nav3Screen classes were RENAMED by R8, which breaks back-stack")
						appendLine("restore after process death (the serial name defaults to the FQCN):")
						renamed.forEach { (from, to) -> appendLine("  - $from -> $to") }
						appendLine("Check `-keep class * implements uk.co.appoly.droid.nav3.Nav3Screen` in")
						appendLine("Nav3Navigation/consumer-rules.pro.")
					}
					if (absent.isNotEmpty()) {
						appendLine("These Nav3Screen classes were not found in mapping.txt at all — either")
						appendLine("R8 removed them, or the demo app no longer references them:")
						absent.forEach { appendLine("  - $it") }
					}
				}
			)
		}

		logger.lifecycle(
			"verifyConsumerKeepRules: ${consumerKeepSentinels.size} consumer-rule-protected classes " +
				"survived R8, and ${nav3NameCriticalScreens.size} Nav3Screen names were preserved unrenamed. ✓"
		)
	}
}