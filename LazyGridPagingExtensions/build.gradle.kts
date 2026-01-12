import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlinKSP)
	alias(libs.plugins.kotlin.compose)
	`maven-publish`
}

group = "com.github.appoly"

android {
	namespace = "uk.co.appoly.droid.lazygridpagingextensions"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	publishing {
		singleVariant("release") {
			withSourcesJar()
		}
	}

	defaultConfig {
		minSdk = BuildConfig.MinSdk.LAZY_PAGING

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
	kotlin {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_11)
		}
	}
	buildFeatures {
		compose = true
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.appcompat)

	api(project(":PagingExtensions"))

	//Compose
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
//	implementation(libs.androidx.material3)

	//Paging
	implementation(libs.paging.runtime)
	implementation(libs.paging.compose)
//	testImplementation(libs.paging.common)

	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
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
