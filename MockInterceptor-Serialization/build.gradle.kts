import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("java-library")
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlinxSerialization)
	alias(libs.plugins.vanniktech.publish)
}


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
	api(project(":MockInterceptor"))
	api(libs.kotlinx.serialization.json)

	testImplementation(libs.junit)
}
mavenPublishing {
    pom {
        name.set("MockInterceptor-Serialization")
        description.set("Type-safe JSON bodies and pagination helpers for MockInterceptor, via kotlinx-serialization.")
    }
}
