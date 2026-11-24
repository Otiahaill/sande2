import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Read build-time configuration from local.properties (git-ignored) or gradle properties.
// This keeps OAuth client IDs and the backend URL out of source control while still
// letting CI inject them from GitHub secrets.
//
// NOTE: `java.util.Properties` must be imported explicitly. In a Kotlin DSL script the
// bare `java` identifier resolves to Gradle's `java` extension, which shadows the
// `java` package and produces "Unresolved reference: util".
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun configValue(key: String, fallback: String = ""): String =
    (project.findProperty(key) as? String)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: fallback

android {
    namespace = "com.example.sande"
    // androidx.credentials 1.3.0 (and googleid 1.1.0) are the newest releases
    // built with Kotlin 1.8 metadata, so compileSdk stays at 34.
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.sande"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Injected build config — see local.properties / gradle properties
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${configValue("SANDE_GOOGLE_CLIENT_ID")}\"")
        buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"${configValue("SANDE_GOOGLE_ANDROID_CLIENT_ID")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${configValue("SANDE_API_BASE_URL")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema export location — required when exportSchema = true
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// Static checks that the Kotlin compiler cannot do.
//
// This project is developed on a phone without a local Gradle build, so the
// CI build is the only compiler. These checks catch the mistakes that are
// invisible to the compiler — unresolved project symbols, DAO calls that do
// not exist, and Room migration SQL that would abort on a real device.
//
// They run before every build, so a mistake fails fast and loudly.
val staticChecks by tasks.registering {
    group = "verification"
    description = "Runs the static analysis scripts in tools/."
    doLast {
        val tools = rootProject.file("tools")
        val scripts = listOf(
            "check_imports.py",
            "check_missing_imports.py",
            "check_dao_calls.py",
            "check_room_queries.py",
            "check_await.py",
            "check_migration_schema.py",
            "check_migration_sql.py"
        )
        scripts.forEach { script ->
            val file = tools.resolve(script)
            if (!file.exists()) {
                logger.warn("Skipping $script — not found")
                return@forEach
            }
            val result = providers.exec {
                workingDir = rootProject.projectDir
                commandLine("python3", file.absolutePath)
                isIgnoreExitValue = true
            }.result.get()
            if (result.exitValue != 0) {
                throw GradleException("$script failed with exit code ${result.exitValue}")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(staticChecks)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)

    // Auth & Credentials
    implementation(libs.androidx.credential.manager)
    implementation(libs.androidx.credential.manager.play.services)
    // GetGoogleIdOption / GoogleIdTokenCredential live here, not in androidx.credentials.
    implementation(libs.google.identity.signin)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Network (Ktor)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx)

    // OkHttp (for Ktor engine)
    implementation(libs.okhttp)

    // EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)

    // Biometric
    implementation(libs.androidx.biometric)

    // Image loading
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.rules)

    // Instrumented tests (Room migration tests) need MigrationTestHelper and the
    // InstrumentationRegistry, which are only available on the androidTest classpath.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}
