plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

group = "dev.bitspittle.limp"
version = libs.versions.limp.lang.get()

// Maximum compatibility
val targetKotlinVersion = "1.5.32"

kotlin {
    jvm()
    js {
        browser()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib", targetKotlinVersion))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test.common)
            implementation(libs.kotlin.test.annotations.common)
            implementation(libs.truthish)
            implementation(libs.kotlinx.coroutines.test)
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