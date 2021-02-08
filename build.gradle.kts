val slf4jVersion: String by project
val ktorVersion: String by project
val kotlinVersion: String by project
val ktxSerializationVersion: String by project
val ktxCoroutinesVersion: String by project
val gqlKtVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven { url = uri("https://kotlin.bintray.com/ktor") }
}

dependencies {
    // Kotlin
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:$kotlinVersion"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:$ktxCoroutinesVersion"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // Ktor main
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))
    api("io.ktor:ktor-server-core")
    api("io.ktor:ktor-websockets")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$ktxSerializationVersion")

    // GraphQL
    api("com.expediagroup:graphql-kotlin-schema-generator:$gqlKtVersion")
    implementation("com.expediagroup:graphql-kotlin-server:$gqlKtVersion")

    testImplementation("io.ktor:ktor-server-tests")
}

sourceSets {
    main {
        java.srcDir("src")
        resources.srcDir("resources")
    }
    test {
        java.srcDir("test")
        resources.srcDir("testresources")
    }
}

kotlin {
    sourceSets["main"].kotlin.srcDirs("src")
    sourceSets["test"].kotlin.srcDirs("test")
}

java {
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
            //javaParameters = true
            //freeCompilerArgs = listOf("-Xemit-jvm-type-annotations")
        }
    }

    publishing {
        publications {
            create<MavenPublication>("ktor-graphql") {
                from(project.components["java"])
                pom {
                    name.set("ktor-graphql")
                    description.set("graphql-ws protocol for ktor websockets")
                    url.set("https://github.com/Gui-Yom/filet")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://github.com/Gui-Yom/filet/blob/master/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("Gui-Yom")
                            name.set("Guillaume Anthouard")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/Gui-Yom/filet.git")
                        developerConnection.set("scm:git:ssh://github.com/Gui-Yom/filet.git")
                        url.set("https://github.com/Gui-Yom/filet/")
                    }
                }
            }
        }
    }
}
