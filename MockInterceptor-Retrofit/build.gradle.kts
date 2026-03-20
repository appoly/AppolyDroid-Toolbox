import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("java-library")
	alias(libs.plugins.kotlin.jvm)
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
	compileOnly(libs.retrofit)
	implementation(libs.kotlin.reflect)

	testImplementation(libs.junit)
	testImplementation(libs.retrofit)
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
