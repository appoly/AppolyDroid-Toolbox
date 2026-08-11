import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlinxSerialization)
	`maven-publish`
}

group = "com.github.appoly"

configure<LibraryExtension> {
	namespace = "uk.co.appoly.droid.nav3"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	publishing {
		singleVariant("release") {
			withSourcesJar()
		}
	}

	defaultConfig {
		minSdk = BuildConfig.MinSdk.NAV3_NAVIGATION

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		consumerProguardFiles("consumer-rules.pro")
	}

	buildTypes {
		release {
			isMinifyEnabled = false
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

dependencies {

	implementation(libs.androidx.core.ktx)

	//Compose
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)

	//Navigation 3 — api: NavKey/NavEntry/NavBackStack/Scene appear in this module's public API
	api(libs.androidx.navigation3.runtime)
	api(libs.androidx.navigation3.ui)
	api(libs.androidx.lifecycle.viewmodel.navigation3)
	// api: ViewModelStoreOwner is the return type of nav3HostViewModelStoreOwner()
	api(libs.androidx.lifecycle.viewmodel)
	// LocalViewModelStoreOwner (host capture for LocalNav3HostViewModelStoreOwner) — internal
	implementation(libs.androidx.lifecycle.viewmodel.compose)

	// KSerializer flows through TabsNav3Navigator.Saver's encode/decode calls — internal.
	// Consumers' @Serializable screens need the serialization plugin on THEIR module; the
	// core annotations reach them compile-scoped via api(navigation3-runtime).
	implementation(libs.kotlinx.serialization.core)

	testImplementation(libs.junit)
	testImplementation(libs.robolectric)
	testImplementation(libs.androidx.junit)
	testImplementation(platform(libs.androidx.compose.bom))
	testImplementation(libs.androidx.ui.test.junit4)
	testImplementation(libs.androidx.ui.test.manifest)
	testImplementation(libs.androidx.material3)
	// On-device suite (see README "On-device test suite"). Deliberately NOT run in CI: it covers
	// only what Robolectric physically cannot reach — a real OnBackPressedDispatcher and real
	// Activity recreation. Run it before tagging a release.
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.test.core.ktx)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.ui.test.junit4)
	androidTestImplementation(libs.androidx.material3)
	androidTestImplementation(libs.androidx.activity.compose)
	debugImplementation(libs.androidx.ui.test.manifest)
}

publishing {
	publications {
		create<MavenPublication>("release") {
			afterEvaluate {
				from(components["release"])
			}
			groupId = "com.github.appoly"
			artifactId = project.name
			version = BuildConfig.TOOLBOX_VERSION
		}
	}
}
