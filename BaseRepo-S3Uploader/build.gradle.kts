plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.android)
	`maven-publish`
}

group = "com.github.appoly"

android {
	namespace = "uk.co.appoly.droid.baserepo.s3"
	compileSdk = libs.versions.compileSdk.get().toInt()

	publishing {
		singleVariant("release") {
			withSourcesJar()
		}
	}

	defaultConfig {
		minSdk = libs.versions.baseRepoMinSdk.get().toInt()

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
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)

	//AppolyDroidBaseRepo
	implementation(project(":BaseRepo"))

	//s3Uploader
	api(project(":S3Uploader"))

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
			version = libs.versions.toolboxVersion.get()
		}
	}
}