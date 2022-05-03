plugins {
    kotlin("jvm")
    `maven-publish`
}

repositories {
    mavenLocal()
    mavenCentral()
    githubPackages("Gui-Yom/graphql-dsl")
}

val ktorVersion: String by project
val kotlinVersion: String by project
val ktxCoroutinesVersion: String by project
val gqlVersion: String by project
val log4jVersion: String by project
val gqlDslVersion: String by project
val reactiveVersion: String by project
val jacksonVersion: String by project

dependencies {
    // Kotlin
    implementation(platform(kotlin("bom", kotlinVersion)))
    implementation(kotlin("stdlib-jdk8"))
    //implementation(kotlin("reflect"))

    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:$ktxCoroutinesVersion"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("org.reactivestreams:reactive-streams:$reactiveVersion")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")

    // Ktor main
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))
    api("io.ktor:ktor-server-core")
    compileOnly("io.ktor:ktor-server-websockets")

    // Serialization
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // GraphQL
    api("com.graphql-java:graphql-java:$gqlVersion")

    testImplementation("io.ktor:ktor-server-test-host") {
        exclude("ch.qos.logback")
    }
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-client-websockets")
    testImplementation("io.ktor:ktor-server-content-negotiation")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("io.ktor:ktor-serialization-jackson")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:$log4jVersion")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    testImplementation("marais.graphql:graphql-dsl:$gqlDslVersion")
    testImplementation("marais.graphql:graphql-dsl-test:$gqlDslVersion")
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
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
        }
    }

    test {
        useJUnitPlatform()
    }
}

publishing {
    repositories {
        githubPackages("Gui-Yom/ktor-graphql")
    }
    publications {
        create<MavenPublication>("root") {
            from(project.components["java"])
            pom {
                name.set("ktor-graphql")
                description.set("A graphql feature for Ktor.")
                url.set("https://github.com/Gui-Yom/ktor-graphql")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/Gui-Yom/ktor-graphql/blob/master/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("Gui-Yom")
                        name.set("Guillaume Anthouard")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/Gui-Yom/ktor-graphql.git")
                    developerConnection.set("scm:git:ssh://github.com/Gui-Yom/ktor-graphql.git")
                    url.set("https://github.com/Gui-Yom/ktor-graphql/")
                }
            }
        }
    }
}

fun RepositoryHandler.githubPackages(path: String) = maven {
    url = uri("https://maven.pkg.github.com/$path")
    name = "GithubPackages"
    credentials {
        username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
        password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
    }
}
