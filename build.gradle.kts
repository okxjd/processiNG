import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlin.io.readText

plugins {
    application
    kotlin("jvm") version "1.5.0"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    kotlin("plugin.serialization") version "1.5.0"
}

val x = File("$projectDir/src/main/resources/build.txt").readText()
group = "space.okxjd"
version = "" // "$x" //-SNAPSHOT"

application.mainClassName = "space.okxjd.processiNG.MainKt"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.slf4j:slf4j-nop:1.7.30")
    implementation("org.apache.poi:poi:5.0.0")
    implementation("org.apache.poi:poi-ooxml:5.0.0")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:0.15.2")
    implementation("com.github.holgerbrandl:krangl:0.16.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = application.mainClassName
    }
  //  minimize()
    exclude(
        "batik-*.jar",
        "fontbox-*.jar",
        "pdfbox-*.jar",
        "graphics2d-*.jar",
        "xmlgraphics-*.jar",
        "bcpkix-jdk15*.jar",
        "bcprov-jdk15*.jar"
    )
}
