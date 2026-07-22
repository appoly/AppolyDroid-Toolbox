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

	//Navigation
	implementation(libs.androidx.navigation.compose)

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
	implementation(project(":MockInterceptor"))
	implementation(project(":MockInterceptor-Serialization"))
	implementation(project(":MockInterceptor-AppolyJson"))
	implementation(project(":MockInterceptor-Retrofit"))

	// For test backend API
	implementation(libs.retrofit)
	implementation(libs.retrofit.serializationConverter)
	implementation(libs.okhttp)
	implementation(libs.okhttp.logging)
	implementation(libs.kotlinx.serialization)
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
)

tasks.register("verifyConsumerKeepRules") {
	group = "verification"
	description = "Asserts AppolyDroid modules' consumer R8 keep rules survive minification (reads release seeds.txt)."
	dependsOn("minifyReleaseWithR8")

	val seedsFile = layout.buildDirectory.file("outputs/mapping/release/seeds.txt")
	inputs.file(seedsFile)
	// Re-run if the expected set changes.
	inputs.property("sentinels", consumerKeepSentinels)

	doLast {
		val file = seedsFile.get().asFile
		if (!file.exists()) {
			throw GradleException(
				"seeds.txt not found at ${file.path}. Expected R8 to produce it during " +
					"minifyReleaseWithR8 — is the release build still minified (isMinifyEnabled = true)?"
			)
		}
		// seeds.txt lists each kept class as a bare FQN line, and kept members as "FQN: <member>".
		val seedLines = file.readLines().toHashSet()
		fun isKept(fqn: String): Boolean =
			seedLines.contains(fqn) || seedLines.any { it.startsWith("$fqn:") }

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
		logger.lifecycle(
			"verifyConsumerKeepRules: all ${consumerKeepSentinels.size} consumer-rule-protected classes survived R8. ✓"
		)
	}
}