plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
    signing
}

publishing {
    repositories {
        maven {
            name = "Central"
            url = uri("https://central.sonatype.com/api/v1/publisher/upload")
            credentials {
                username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME").orNull
                password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").orNull
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.absmartly"
            artifactId = "absmartly-sdk-kotlin"
            version = providers.gradleProperty("versionName").orElse("1.0.0").get()
        }
    }
}

group = "com.absmartly"
version = providers.gradleProperty("versionName").orElse("1.0.0").get()

signing {
    val signingKey: String? = providers.gradleProperty("signingInMemoryKey").orNull
    val signingPassword: String? = providers.gradleProperty("signingInMemoryKeyPassword").orNull
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = false
        events("passed", "failed", "skipped")
    }
    afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
        if (desc.parent == null) {
            println("\n${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped")
        }
    }))
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}
