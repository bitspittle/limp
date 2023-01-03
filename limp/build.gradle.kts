plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

group = "dev.bitspittle.limp"
version = libs.versions.limp.lang

repositories {
    maven("https://us-central1-maven.pkg.dev/varabyte-repos/public")
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }
    sourceSets {
        all {
            // For "runTest"
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }

        val commonMain by getting {
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test.common)
                implementation(libs.kotlin.test.annotations.common)
                implementation(libs.truthish.common)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.truthish.jvm)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(libs.truthish.js)
            }
        }
    }
}

publishing {
    repositories {
        maven {
            group = project.group
            version = libs.versions.limp.lang.get()
        }
    }
}