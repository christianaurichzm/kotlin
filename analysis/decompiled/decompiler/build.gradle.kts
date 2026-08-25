import org.jetbrains.kotlin.build.foreign.CheckForeignClassUsageTask
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("common-configuration")
    id("test-federation-convention")
    id("com.autonomousapps.dependency-analysis")
    kotlin("jvm")
    id("kotlin-git.gradle-build-conventions.foreign-class-usage-checker")
    id("project-tests-convention")
    id("test-inputs-check")
}

dependencies {
    implementation(project(":compiler:psi:psi-api"))
    implementation(project(":core:deserialization.common"))
    implementation(project(":kotlin-util-klib"))

    api(intellijCore())

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(testFixtures(project(":compiler:psi:psi-api")))
    testImplementation(testFixtures(project(":compiler:tests-common")))
    testCompileOnly(intellijCore())
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

private val stableNonPublicMarkers = listOf(
    "org.jetbrains.kotlin.psi.KtImplementationDetail",
    "org.jetbrains.kotlin.psi.KtNonPublicApi",
    "org.jetbrains.kotlin.psi.KtExperimentalApi",
)

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude.annotatedWith.addAll(stableNonPublicMarkers)
        }
    }
}

projectTests {
    testCodebaseTask()
}

val checkForeignClassUsage = tasks.register("checkForeignClassUsage", CheckForeignClassUsageTask::class) {
    outputFile = file("api/decompiler.foreign")
    nonPublicMarkers.addAll(stableNonPublicMarkers)
}
