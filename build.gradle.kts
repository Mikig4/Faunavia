import java.time.Instant

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

providers.environmentVariable("FAUNAVIA_BUILD_ROOT").orNull
    ?.takeIf { it.isNotBlank() }
    ?.let { externalBuildRoot ->
        layout.buildDirectory.set(file("$externalBuildRoot/root"))
        subprojects {
            val modulePath = path.removePrefix(":").replace(':', '/')
            layout.buildDirectory.set(file("$externalBuildRoot/$modulePath"))
        }
    }

val reportRoot = layout.buildDirectory.dir("reports/verification")
val appBuildDirectory = project(":app").layout.buildDirectory
val domainBuildDirectory = project(":core:domain").layout.buildDirectory
val testingBuildDirectory = project(":core:testing").layout.buildDirectory
val localBuildDirectory = project(":core:local").layout.buildDirectory

val verifyF0 = tasks.register<Exec>("verifyF0") {
    group = "verification"
    description = "Replays the versioned F0 data spike tests."
    commandLine("pwsh", "-NoProfile", "-File", file("f0/scripts/verify-f0.ps1").absolutePath)
}

val formatCheck = tasks.register<Exec>("formatCheck") {
    group = "verification"
    description = "Checks source formatting invariants without rewriting files."
    commandLine("pwsh", "-NoProfile", "-File", file("scripts/quality/check-format.ps1").absolutePath)
}

val staticAnalysis = tasks.register<Exec>("staticAnalysis") {
    group = "verification"
    description = "Checks architectural boundaries and forbidden source patterns."
    commandLine("pwsh", "-NoProfile", "-File", file("scripts/quality/check-boundaries.ps1").absolutePath)
}

val verifyFast = tasks.register("verifyFast") {
    group = "verification"
    description = "Builds, lints, statically checks and runs all host-side tests."
    dependsOn(
        formatCheck,
        staticAnalysis,
        ":core:domain:test",
        ":core:testing:test",
        ":core:local:lintDebug",
        ":app:testDebugUnitTest",
        ":app:lintDebug",
        ":app:assembleDebug"
    )
}

val verifyDevice = tasks.register("verifyDevice") {
    group = "verification"
    description = "Installs, launches and tests the app on the managed Android device."
    dependsOn(":app:pixel2Api36DebugAndroidTest")
    doLast {
        val declaredTests = fileTree("app/src/androidTest") { include("**/*.kt") }.files
            .flatMap { file ->
                Regex("""@Test\s+fun\s+(\w+)\s*\(""").findAll(file.readText()).map { it.groupValues[1] }.toList()
            }.toSet()
        val executedTests = fileTree(appBuildDirectory.get().dir("outputs/androidTest-results")) {
            include("**/*.xml")
        }.files.flatMap { file ->
            Regex("""<testcase\s+name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        check(declaredTests.isNotEmpty()) { "No instrumented tests were discovered in source." }
        check(executedTests.containsAll(declaredTests)) {
            "Instrumented tests silently omitted by runner: ${declaredTests - executedTests}"
        }
    }
}

val verifyVisual = tasks.register("verifyVisual") {
    group = "verification"
    description = "Verifies that the managed-device run executed the screenshot golden test."
    dependsOn(verifyDevice)
    doLast {
        val resultFiles = fileTree(appBuildDirectory.get().dir("outputs/androidTest-results")) {
            include("**/*.xml")
        }.files
        val goldenWasRun = resultFiles.any { file ->
            file.readText().contains("HomeGoldenTest")
        }
        check(goldenWasRun) {
            "No managed-device result contains HomeGoldenTest; visual verification is incomplete."
        }
        val report = reportRoot.get().file("visual-summary.txt").asFile
        report.parentFile.mkdirs()
        report.writeText("PASS HomeGoldenTest ${Instant.now()}\n")
    }
}

val collectVerificationReports = tasks.register("collectVerificationReports") {
    group = "verification"
    description = "Builds a stable index for all verification reports."
    dependsOn(verifyF0, verifyFast, verifyVisual)
    doLast {
        val destination = reportRoot.get().asFile
        val appBuild = appBuildDirectory.get().asFile
        val domainBuild = domainBuildDirectory.get().asFile
        val testingBuild = testingBuildDirectory.get().asFile
        val localBuild = localBuildDirectory.get().asFile
        destination.mkdirs()
        val report = destination.resolve("index.html")
        report.writeText(
            """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>Faunavia verification</title></head>
            <body><h1>Faunavia verification: PASS</h1>
            <p>Generated ${Instant.now()}</p>
            <ul>
              <li><a href="${appBuild.resolve("reports/lint-results-debug.html").toURI()}">Android lint</a></li>
              <li><a href="${localBuild.resolve("reports/lint-results-debug.html").toURI()}">Local storage lint</a></li>
              <li><a href="${appBuild.resolve("reports/tests/testDebugUnitTest/index.html").toURI()}">App JVM tests</a></li>
              <li><a href="${domainBuild.resolve("reports/tests/test/index.html").toURI()}">Domain JVM tests</a></li>
              <li><a href="${testingBuild.resolve("reports/tests/test/index.html").toURI()}">Testing fakes JVM tests</a></li>
              <li><a href="${appBuild.resolve("reports/androidTests/managedDevice/debug/allDevices/index.html").toURI()}">Room CRUD, integrity, migration, UI and screenshot tests</a></li>
            </ul></body></html>
            """.trimIndent()
        )
    }
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs F0-F2 host, database, device and visual gates and collects reports."
    dependsOn(collectVerificationReports)
}

tasks.register("failureProbe") {
    group = "verification"
    description = "Intentionally fails after publishing an HTML diagnostic report."
    doLast {
        val report = reportRoot.get().file("failure-probe.html").asFile
        report.parentFile.mkdirs()
        report.writeText(
            """
            <!doctype html><html lang="en"><head><meta charset="utf-8"><title>Failure probe</title></head>
            <body><h1>Expected verification failure</h1><p>The report was written before the task failed.</p></body></html>
            """.trimIndent()
        )
        throw GradleException("Intentional F1 failure probe; report: ${report.absolutePath}")
    }
}
