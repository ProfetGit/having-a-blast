plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.18.2" apply false
    id("net.neoforged.moddev") version "2.0.147" apply false
    id("net.minecraftforge.gradle") version "7.0.40" apply false
}

stonecutter active "26.3-fabric"

stonecutter parameters {
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge", "forge")
}

tasks.register<Copy>("dist") {
    group = "build"
    description = "Builds every target and copies the jars into dist/"
    val skip = providers.gradleProperty("hab.skip").orNull?.split(',')?.map { it.trim() }.orEmpty()
    stonecutter.versions.filter { v -> skip.none { v.project.endsWith("-$it") || v.project == it } }.forEach { v ->
        val p = project(":${v.project}")
        dependsOn(p.tasks.named("build"))
        from(p.layout.buildDirectory.dir("libs")) {
            include("havingablast-*.jar")
            exclude("*-sources.jar", "*-dev.jar")
        }
    }
    // the ForgeGradle 6 side projects (forge-1.21.1, forge-1.20.1) publish their jars here when mt.py / port.py build them;
    // 1.20.1 NeoForge runs the Forge jar and has a copy of its own
    from(layout.projectDirectory.dir("versions")) {
        include("1.21.1-forge/build/libs/havingablast-*.jar", "1.20.1-forge/build/libs/havingablast-*.jar", "1.20.1-neoforge/build/libs/havingablast-*.jar")
        exclude("**/*-sources.jar", "**/*-dev.jar", "**/*-slim.jar")
        eachFile { relativePath = RelativePath(true, name) }
        includeEmptyDirs = false
    }
    into(layout.projectDirectory.dir("dist"))
    doFirst { delete(layout.projectDirectory.dir("dist")) }
}
