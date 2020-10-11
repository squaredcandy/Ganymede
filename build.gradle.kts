import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "1.4.10"
    kotlin("kapt") version "1.4.10"
    id("com.google.protobuf") version "0.8.13"
    maven
}

group = "org.squaredcandy"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    jcenter()
}

sourceSets {
    getByName("main") {
        proto.srcDir("src/main/proto")
        java.srcDir("build/generated/source/proto/main/java")
    }
}

val protobuf = "3.13.0"
val grpc = "1.32.1"
val kroto = "0.6.1"

dependencies {
    val coroutine = "1.3.9"
    val truth = "1.0.1"
    val h2 = "1.4.199"
    val javax = "1.3.2"
    val turbine = "0.2.1"
    val io = "0.0.4"
    val europa = "0.0.6"

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutine")

    implementation("javax.annotation:javax.annotation-api:$javax")
    runtimeOnly("io.grpc:grpc-netty-shaded:$grpc")
    implementation("io.grpc:grpc-protobuf:$grpc")
    implementation("io.grpc:grpc-stub:$grpc")
    implementation("com.github.marcoferrer.krotoplus:kroto-plus-coroutines:$kroto")

    implementation("com.github.squaredcandy:Io:$io")
    implementation("com.github.squaredcandy:Europa:$europa")

    testImplementation("app.cash.turbine:turbine:$turbine")
    testImplementation("com.google.truth:truth:$truth")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutine")
    testImplementation("com.h2database:h2:$h2")
    testImplementation(kotlin("test-junit5"))
}
tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
tasks.test {
    useJUnitPlatform()
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