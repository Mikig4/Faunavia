plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.coroutines.core)
    testImplementation(libs.junit4)
}

tasks.test {
    useJUnit()
    reports.html.required.set(true)
    reports.junitXml.required.set(true)
}
