plugins {
    id("common-configuration")
    id("test-federation-convention")
    id("com.autonomousapps.dependency-analysis")
    kotlin("jvm")
}

dependencies {
    implementation(project(":compiler:psi:psi-api"))
    implementation(project(":core:deserialization.common"))
    implementation(project(":kotlin-util-klib"))

    api(intellijCore())
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}
