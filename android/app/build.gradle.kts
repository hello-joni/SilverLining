import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.joni.silverlining"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.joni.silverlining"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// --- Bundled silverbullet binary -----------------------------------------
// Downloads the upstream pre-built linux/aarch64 binary, verifies SHA256,
// extracts to build/silverbullet-jni/arm64-v8a/libsilverbullet.so so AGP
// packages it into the APK. Pin version + hash in gradle.properties.

val silverbulletVersion: String by project
val silverbulletSha256: String by project

val downloadSilverbullet by tasks.registering {
    val version = silverbulletVersion
    val expectedSha = silverbulletSha256
    val downloadUrl = "https://github.com/silverbulletmd/silverbullet/releases/download/$version/silverbullet-server-linux-aarch64.zip"
    val outputBinary = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libsilverbullet.so")

    inputs.property("version", version)
    inputs.property("sha256", expectedSha)
    outputs.file(outputBinary)

    doLast {
        val cacheDir = layout.buildDirectory.dir("silverbullet-cache").get().asFile
        cacheDir.mkdirs()
        val zipFile = cacheDir.resolve("silverbullet-$version.zip")

        if (!zipFile.exists() || sha256OfFile(zipFile) != expectedSha) {
            logger.lifecycle("Downloading silverbullet $version from $downloadUrl")
            URI(downloadUrl).toURL().openStream().use { input ->
                zipFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val actualSha = sha256OfFile(zipFile)
        check(actualSha == expectedSha) {
            "\nSHA256 mismatch for silverbullet $version\n" +
                "  expected: ${if (expectedSha.isEmpty()) "(empty)" else expectedSha}\n" +
                "  actual:   $actualSha\n" +
                "Update silverbulletSha256 in gradle.properties.\n"
        }

        val outFile = outputBinary.asFile
        outFile.parentFile.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "silverbullet" || entry.name.endsWith("/silverbullet")) {
                    outFile.outputStream().use { out -> zip.copyTo(out) }
                    break
                }
                entry = zip.nextEntry
            }
        }
        check(outFile.exists() && outFile.length() > 0) {
            "Failed to extract silverbullet binary from $zipFile"
        }
        outFile.setExecutable(true)
    }
}

fun sha256OfFile(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.named("preBuild") {
    dependsOn(downloadSilverbullet)
}