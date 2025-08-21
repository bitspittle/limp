plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

group = "dev.bitspittle.limp"
version = libs.versions.limp.lang.get()

kotlin {
    jvm()
    js {
        browser()
    }
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test.common)
            implementation(libs.kotlin.test.annotations.common)
            implementation(libs.truthish.common)
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