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
	api(project(":MockInterceptor"))
	compileOnly(libs.retrofit)
	implementation(libs.kotlin.reflect)

	testImplementation(libs.junit)
	testImplementation(libs.retrofit)
}
mavenPublishing {
    pom {
        name.set("MockInterceptor-Retrofit")
        description.set("Registers MockInterceptor routes automatically by reflecting over Retrofit annotations.")
    }
}
