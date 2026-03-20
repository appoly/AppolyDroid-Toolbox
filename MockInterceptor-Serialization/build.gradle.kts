import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("java-library")
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlinxSerialization)
	`maven-publish`
}

group = "com.github.appoly"

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
	withSourcesJar()
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

dependencies {
	api(project(":MockInterceptor"))
	api(libs.kotlinx.serialization)

	testImplementation(libs.junit)
}

publishing {
	publications {
		create<MavenPublication>("release") {
			from(components["java"])
			groupId = "com.github.appoly"
			artifactId = project.name
			version = BuildConfig.TOOLBOX_VERSION
		}
	}
}
