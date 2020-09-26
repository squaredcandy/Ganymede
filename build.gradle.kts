import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "1.4.10"
    kotlin("kapt") version "1.4.10"
    id("com.google.protobuf") version "0.8.13"
}

group = "org.squaredcandy"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    jcenter()
}

sourceSets {
    get("main").proto {
        srcDir("src/main/proto")
    }
    getByName("main") {
        java.srcDir("build/generated/source/proto/main/java")
    }
}

val protobuf = "3.13.0"
val grpc = "1.32.1"
val kroto = "0.6.1"

dependencies {
    val coroutine = "1.3.9"
    val truth = "1.0.1"
    val javax = "1.3.2"

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutine")

    implementation("javax.annotation:javax.annotation-api:$javax")
    runtimeOnly("io.grpc:grpc-netty-shaded:$grpc")
    implementation("io.grpc:grpc-protobuf:$grpc")
    implementation("io.grpc:grpc-stub:$grpc")
    implementation("com.github.marcoferrer.krotoplus:kroto-plus-coroutines:$kroto")

    implementation("com.github.squaredcandy:Io:0.0.1")
    implementation("com.github.squaredcandy:Europa:0.0.3")

    testImplementation("com.google.truth:truth:$truth")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutine")
    testImplementation(kotlin("test-junit5"))
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobuf" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpc" }
        id("kroto") {
            artifact = "com.github.marcoferrer.krotoplus:protoc-gen-kroto-plus:$kroto"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach { task ->
            task.inputs.files("krotoPlusConfig.yaml")
//            task.builtins {
//                id("java") { option("lite") }
//            }
            task.plugins {
                id("grpc") {
                    outputSubDir = "java"
//                    option("lite")
                }
                id("kroto") {
                    outputSubDir = "java"
                    option("ConfigPath=krotoPlusConfig.yaml")
                }
            }
        }
    }
}