plugins {
    kotlin("jvm") version "2.1.20"
}

/**
 * Módulo JVM para la lógica pura del plugin.
 *
 * Compila `ApkUpdaterLogic.kt` (sin Android de por medio) y lo testea con
 * JUnit. Corre en segundos y no necesita emulador ni Robolectric: es la red de
 * seguridad para el parseo de ids, el cálculo de progreso y la comparación de
 * checksums.
 *
 *   ./gradlew test
 */

val stageSources = tasks.register<Sync>("stageSources") {
    from(layout.projectDirectory.dir("../../resources/android/src/com/homeside/apkupdater")) {
        include("ApkUpdaterLogic.kt")
    }
    into(layout.buildDirectory.dir("generated/logic"))
}

kotlin {
    jvmToolchain(17)
}

sourceSets["main"].kotlin.srcDir(stageSources.map { it.destinationDir })

tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn(stageSources)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}
