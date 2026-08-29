plugins {
    id("com.android.application") version "8.9.2" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Force a single Kotlin stdlib version to avoid duplicate-class conflicts
subprojects {
    configurations.all {
        resolutionStrategy {
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:1.8.22",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22"
            )
        }
    }
}