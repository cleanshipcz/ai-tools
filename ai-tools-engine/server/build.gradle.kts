plugins {
    alias(libs.plugins.cleanship.kotlin.library)
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":utils"))
    implementation(project(":telemetry"))

    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)
    implementation(libs.ktorServerStatusPages)

    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.clikt)

    testImplementation(kotlin("test"))
}

application {
    mainClass = "cz.cleanship.aitools.server.ServerAppKt"
}

val frontendDir = layout.projectDirectory.dir("frontend")
val frontendDist = frontendDir.dir("dist")
val staticResourcesDir = layout.buildDirectory.dir("resources/main/static")

val installFrontend by tasks.registering(Exec::class) {
    workingDir = frontendDir.asFile
    commandLine("npm", "ci")
    inputs.file(frontendDir.file("package.json"))
    inputs.file(frontendDir.file("package-lock.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val buildFrontend by tasks.registering(Exec::class) {
    dependsOn(installFrontend)
    workingDir = frontendDir.asFile
    commandLine("npm", "run", "build")
    inputs.dir(frontendDir.dir("src"))
    inputs.file(frontendDir.file("package.json"))
    inputs.file(frontendDir.file("index.html"))
    inputs.file(frontendDir.file("vite.config.ts"))
    inputs.file(frontendDir.file("tsconfig.json"))
    outputs.dir(frontendDist)
}

val copyFrontend by tasks.registering(Copy::class) {
    dependsOn(buildFrontend)
    from(frontendDist)
    into(staticResourcesDir)
}

tasks.named("processResources") {
    dependsOn(copyFrontend)
}
