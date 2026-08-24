import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("java-library")
	alias(libs.plugins.kotlin.jvm)
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
	api(libs.okhttp)
	api(libs.flexiLogger)

	testImplementation(libs.junit)
}
mavenPublishing {
    pom {
        name.set("MockInterceptor")
        description.set("An OkHttp interceptor that serves mocked responses from a route-matching DSL, with no mock server required.")
    }
}
