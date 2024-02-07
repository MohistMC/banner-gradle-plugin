package com.mohistmc.banner.gradle

import com.mohistmc.banner.gradle.tasks.ProcessMappingV2Task
import com.mohistmc.banner.gradle.tasks.RemapSpigotTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException

import java.security.MessageDigest

class BannerGradlePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def bannerExt = project.extensions.create('banner', BannerExtension, project)
        def conf = project.configurations.create('banner')
        project.configurations.implementation.extendsFrom(conf)
        def buildTools = project.file("${bannerExt.sharedSpigot ? project.rootProject.buildDir : project.buildDir}/banner_cache/buildtools")
        def buildToolsFile = new File(buildTools, 'BuildTools.jar')
        def downloadSpigot
        if (project.rootProject.tasks.findByPath('downloadBuildTools')) {
            downloadSpigot = project.rootProject.tasks.getByName('downloadBuildTools')
        } else {
            downloadSpigot = project.rootProject.tasks.create('downloadBuildTools', com.mohistmc.banner.gradle.tasks.DownloadBuildToolsTask, {
                it.output = buildToolsFile
            })
            downloadSpigot.doFirst {
                if (buildToolsFile.exists()) throw new StopExecutionException()
            }
        }
        def buildSpigot
        if (project.rootProject.tasks.findByPath('buildSpigotTask')) {
            buildSpigot = project.rootProject.tasks.getByName('buildSpigotTask')
        } else {
            buildSpigot = project.rootProject.tasks.create('buildSpigotTask', com.mohistmc.banner.gradle.tasks.BuildSpigotTask, project)
            project.afterEvaluate {
                buildSpigot.doFirst {
                    if (new File(buildTools, "spigot-${bannerExt.mcVersion}.jar").exists()) {
                        throw new StopExecutionException()
                    }
                }
                buildSpigot.configure { com.mohistmc.banner.gradle.tasks.BuildSpigotTask task ->
                    task.buildTools = buildToolsFile
                    task.outputDir = buildTools
                    task.mcVersion = bannerExt.mcVersion
                    task.dependsOn(downloadSpigot)
                }
            }
        }

        def processMapping = project.tasks.create('processMapping', ProcessMappingV2Task)
        def remapSpigot = project.tasks.create('remapSpigotJar', RemapSpigotTask)
        project.afterEvaluate {
            processMapping.configure { ProcessMappingV2Task task ->
                task.buildData = new File(buildTools, 'BuildData')
                task.mcVersion = bannerExt.mcVersion
                task.bukkitVersion = bannerExt.bukkitVersion
                task.outDir = project.file("${project.buildDir}/banner_cache/tmp_srg")
                task.inJar = new File(buildTools, "spigot-${bannerExt.mcVersion}.jar")
                task.inVanillaJar = new File(buildTools, "work/minecraft_server.${task.mcVersion}.jar")
                task.packageName = bannerExt.packageName
                task.dependsOn(buildSpigot)
            }
            remapSpigot.configure { RemapSpigotTask task ->
                task.ssJar = new File(buildTools, 'BuildData/bin/SpecialSource.jar')
                task.inJar = new File(buildTools, "Spigot/Spigot-Server/target/spigot-${bannerExt.mcVersion}-R0.1-SNAPSHOT-remapped-mojang.jar")
                task.outJar = project.file("libs/spigot-${bannerExt.mcVersion}-mapped.jar")
                task.inApiJar = new File(buildTools, "Spigot/Spigot-API/target/spigot-api-${bannerExt.mcVersion}-R0.1-SNAPSHOT.jar")
                task.outApiJar = project.file("libs/spigot-api-${bannerExt.mcVersion}-mapped.jar")
                task.outDeobf = project.file("libs/spigot-${bannerExt.mcVersion}-mapped-deobf.jar")
                task.dependsOn(buildSpigot)
                if (!task.bukkitVersion) {
                    task.bukkitVersion = bannerExt.bukkitVersion
                }
            }
            project.tasks.compileJava.dependsOn(remapSpigot)
        }
    }

    private static def sha1(file) {
        MessageDigest md = MessageDigest.getInstance('SHA-1')
        file.eachByte 4096, { bytes, size ->
            md.update(bytes, 0 as byte, size)
        }
        return md.digest().collect { String.format "%02x", it }.join()
    }

    private static Map<String, String> artifacts(Project project, List<String> arts) {
        def ret = new HashMap<String, String>()
        def cfg = project.configurations.create("art_rev_" + System.currentTimeMillis())
        cfg.transitive = false
        arts.each {
            def dep = project.dependencies.create(it)
            cfg.dependencies.add(dep)
        }
        cfg.resolve()
        cfg.resolvedConfiguration.resolvedArtifacts.each { it ->
            def art = [
                    group     : it.moduleVersion.id.group,
                    name      : it.moduleVersion.id.name,
                    version   : it.moduleVersion.id.version,
                    classifier: it.classifier,
                    extension : it.extension,
                    file      : it.file
            ]
            def desc = "${art.group}:${art.name}:${art.version}"
            if (art.classifier != null)
                desc += ":${art.classifier}"
            if (art.extension != 'jar')
                desc += "@${art.extension}"
            ret.put(desc.toString(), sha1(art.file))
        }
        return arts.collectEntries { [(it.toString()): ret.get(it.toString())] }
    }
}
