plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
    signing
}

repositories {
    mavenLocal()
    githubPackages("Gui-Yom/graphql-dsl")
    mavenCentral()
    jcenter()
    maven { url = uri("https://kotlin.bintray.com/ktor") }
}

val slf4jVersion: String by project
val ktorVersion: String by project
val kotlinVersion: String by project
val ktxSerializationVersion: String by project
val ktxCoroutinesVersion: String by project
val gqlVersion: String by project
val gqlDslVersion: String by project
val reactiveVersion: String by project

dependencies {
    // Kotlin
    implementation(platform(kotlin("bom", kotlinVersion)))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:$ktxCoroutinesVersion"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("org.reactivestreams:reactive-streams:$reactiveVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // Ktor main
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))
    api("io.ktor:ktor-server-core")
    api("io.ktor:ktor-websockets")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$ktxSerializationVersion")

    // GraphQL
    api("com.graphql-java:graphql-java:$gqlVersion")

    testImplementation("io.ktor:ktor-server-tests")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-serialization")
    testImplementation("marais:graphql-dsl:$gqlDslVersion")
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
            useIR = true
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
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

signing {
    sign(publishing.publications["root"])
}

fun RepositoryHandler.githubPackages(path: String) = maven {
    url = uri("https://maven.pkg.github.com/$path")
    name = "GithubPackages"
    credentials {
        username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
        password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
    }
}
