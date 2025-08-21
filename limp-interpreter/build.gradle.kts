plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "dev.bitspittle.limp.interpreter"
version = libs.versions.limp.interpreter.get()

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":limp"))
}

application {
    mainClass.set("MainKt")
}
