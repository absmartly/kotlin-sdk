plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

java {
    withSourcesJar()
    withJavadocJar()
}

// Same staging API as the java-sdk: the Central Portal's OSSRH-compatible endpoint.
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
            password.set(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.absmartly"
            artifactId = "absmartly-sdk-kotlin"
            pom {
                name.set("ABsmartly Kotlin SDK")
                description.set("ABsmartly SDK for Kotlin")
                url.set("https://github.com/absmartly/kotlin-sdk")
                licenses { license { name.set("The Apache License, Version 2.0"); url.set("http://www.apache.org/licenses/LICENSE-2.0.txt") } }
                developers { developer { id.set("absmartly"); name.set("ABsmartly"); url.set("https://www.absmartly.com") } }
                scm { url.set("https://github.com/absmartly/kotlin-sdk"); connection.set("scm:git:https://github.com/absmartly/kotlin-sdk.git"); developerConnection.set("scm:git:ssh://git@github.com/absmartly/kotlin-sdk.git") }
            }
        }
    }
}

group = "com.absmartly"
version = providers.gradleProperty("versionName").orElse("1.0.0").get()

val signingKey: String? = providers.environmentVariable("SIGNING_KEY").orNull
val signingPassword: String? = providers.environmentVariable("SIGNING_PASSWORD").orNull

// Matches the upload task itself, so a direct publishMavenPublicationToSonatypeRepository is covered as well as
// the publishToSonatype lifecycle task that depends on it.
fun TaskExecutionGraph.publishesToSonatype() =
    allTasks.any { it is PublishToMavenRepository && it.repository.name == "sonatype" }

signing {
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications["maven"])
    // Local publishToMavenLocal stays unsigned; anything bound for Central must be signed.
    setRequired { gradle.taskGraph.publishesToSonatype() }
}

// Fail before anything is staged, rather than letting Central reject an unsigned release.
gradle.taskGraph.whenReady {
    if (publishesToSonatype() && (signingKey.isNullOrBlank() || signingPassword.isNullOrBlank())) {
        throw GradleException("SIGNING_KEY and SIGNING_PASSWORD must be set to publish to Maven Central")
    }
}

// SdkVersion is generated from the build version, so X-Agent and sdk_version always match the published artifact.
val generateSdkVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sdkversion")
    val sdkVersion = project.version.toString()
    inputs.property("version", sdkVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/absmartly/sdk/SdkVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.absmartly.sdk
            |
            |/** SDK identity sent in the X-Agent header and optional system attributes. */
            |object SdkVersion {
            |    const val NAME = "absmartly-kotlin-sdk"
            |    const val VERSION = "$sdkVersion"
            |    const val AGENT = "${'$'}NAME/${'$'}VERSION"
            |}
            |""".trimMargin()
        )
    }
}

kotlin.sourceSets.named("main") { kotlin.srcDir(generateSdkVersion) }

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
    systemProperty("absmartly.expectedSdkVersion", project.version.toString())
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
