// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
plugins { id("com.android.application") }
android {
    namespace = "io.github.z2bguoguo.gandroidtools"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.z2bguoguo.gandroidtools"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.2.0-dev"
        testInstrumentationRunner = "io.github.z2bguoguo.gandroidtools.GatTestRunner"
    }
    if (System.getenv("CI") == "true") { installation { installOptions.add("-g") } }
    buildFeatures { aidl = true; buildConfig = true }
    signingConfigs {
        create("release") {
            System.getenv("SIGNING_STORE_FILE")?.let { storeFile = file(it) }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("androidx.fragment:fragment:1.8.6")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}

tasks.register("verifyReleaseSigning") {
    doLast { listOf("SIGNING_STORE_FILE", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD").forEach { require(!System.getenv(it).isNullOrBlank()) { "Missing persistent signing configuration: $it" } } }
}
tasks.matching { it.name == "packageRelease" }.configureEach { dependsOn("verifyReleaseSigning") }

val javaFormatter by configurations.creating
javaFormatter.isTransitive = false
dependencies { javaFormatter("com.google.googlejavaformat:google-java-format:1.25.2:all-deps") }
listOf("spotlessApply", "spotlessCheck").forEach { taskName ->
    tasks.register<JavaExec>(taskName) {
        classpath = javaFormatter
        mainClass.set("com.google.googlejavaformat.java.Main")
        listOf("api", "file", "parser", "tree", "util", "code").forEach { jvmArgs("--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED") }
        doFirst {
            args = (if (taskName == "spotlessApply") listOf("--replace") else listOf("--dry-run", "--set-exit-if-changed")) + fileTree("src") { include("**/*.java") }.files.sortedBy { it.path }.map { it.absolutePath }
        }
    }
}

// Package the repository license verbatim for offline viewing.
val licenseAssets = layout.buildDirectory.dir("generated/licenseAssets")
val prepareLicenseAssets by tasks.registering(Copy::class) {
    from(rootProject.projectDir) { include("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", "signing-certificate.sha256") }
    into(licenseAssets)
}
android.sourceSets.getByName("main").assets.srcDir(licenseAssets)
tasks.named("preBuild") { dependsOn(prepareLicenseAssets) }
