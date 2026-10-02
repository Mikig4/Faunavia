plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    implementation(project(":core:network"))
    api(project(":core:domain"))
    api(project(":core:route"))
    api(project(":core:occurrence"))
    api(project(":core:plausibility"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.core)
    testImplementation(libs.junit4)
}

tasks.test {
    useJUnit()
    reports.html.required.set(true)
    reports.junitXml.required.set(true)
}
