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
	api(project(":MockInterceptor-Serialization"))

	testImplementation(libs.junit)
}
mavenPublishing {
    pom {
        name.set("MockInterceptor-AppolyJson")
        description.set("MockInterceptor helpers for Appoly's standard JSON envelope.")
    }
}
