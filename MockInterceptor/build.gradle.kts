import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("java-library")
	alias(libs.plugins.kotlin.jvm)
	`maven-publish`
}

group = "com.github.appoly.AppolyDroid-Toolbox"

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

dependencies {
	api(libs.okhttp)
	api(libs.flexiLogger)

	testImplementation(libs.junit)
}

publishing {
	publications {
		create<MavenPublication>("release") {
			from(components["java"])
			groupId = "com.github.appoly.AppolyDroid-Toolbox"
			artifactId = project.name
			version = BuildConfig.TOOLBOX_VERSION
		}
	}
}
