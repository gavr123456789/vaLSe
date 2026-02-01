plugins {
    kotlin("jvm") version "2.3.0"
    id("org.graalvm.buildtools.native") version "0.10.3"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.github.gavr123456789:niva:0.1")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("org.example.MainKt")
        }
    }
    binaries.all {

        imageName.set("valse")
        buildArgs.add("-O3")

        // temp solution
//        if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
//            buildArgs.add("--static")
//            buildArgs.add("--libc=musl")
        //
//        }
        this.runtimeArgs()
        buildArgs.add("--no-fallback")
//        buildArgs.add("-march=native")
        buildArgs.add("--initialize-at-build-time")
    }
}

application {
    mainClass = "org.example.MainKt"
}
