val slf4jVersion: String by project
val ktorVersion: String by project
val kotlinVersion: String by project
val ktxSerializationVersion: String by project
val ktxCoroutinesVersion: String by project
val gqlKtVersion: String by project

dependencies {
    api(project(":"))

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

    testImplementation("io.ktor:ktor-server-tests")
}
