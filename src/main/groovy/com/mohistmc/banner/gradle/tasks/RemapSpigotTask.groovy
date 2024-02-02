package com.mohistmc.banner.gradle.tasks

import net.md_5.specialsource.SpecialSource
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class RemapSpigotTask extends DefaultTask {

    private File ssJar
    private File inJar
    private File outJar
    private File outDeobf
    private List<String> includes
    private List<String> excludes
    private String bukkitVersion
    private File inSrg
    private File inAt
    private File inApiJar
    private File outApiJar
    private File inApiAt
    private List<String> includesApi
    private List<String> excludesApi

    RemapSpigotTask() {
        includes = new ArrayList<>()
        includes.add('configurations')
        includes.add('META-INF/maven/org.spigotmc')
        includes.add('org/spigotmc')
        includes.add('org/bukkit/craftbukkit')
        includes.add('version.json')
        excludes = new ArrayList<>()
        excludes.add('org/bukkit/craftbukkit/libs/it')
        excludes.add('org/bukkit/craftbukkit/libs/org/apache')
        excludes.add('org/bukkit/craftbukkit/libs/org/codehaus')
        excludes.add('org/bukkit/craftbukkit/libs/org/eclipse')
        excludes.add('org/bukkit/craftbukkit/libs/jline')
        excludes.add('org/bukkit/craftbukkit/Main')
        includesApi = new ArrayList<>()
        includesApi.add("org/bukkit")
        includesApi.add("org/spigotmc")
        excludesApi = new ArrayList<>()
        excludesApi.add('org/bukkit/plugin/java/LibraryLoader')
        excludesApi.add('org/bukkit/plugin/java/PluginClassLoader')
        excludesApi.add('org/bukkit/plugin/java/JavaPluginLoader')
        excludesApi.add('org/bukkit/event/Event')
        excludesApi.add('org/bukkit/Material')
        File libDir = new File(project.rootDir, "/libs")
        inSrg = new File(libDir, "banner-extra.srg")
        inAt = new File(libDir, "bukkit.at")
        inApiAt = new File(libDir, "bukkit_api.at")
    }

    @TaskAction
    void remap() {
        def tmp = Files.createTempFile("banner", "jar")
        SpecialSource.main(new String[]{
                '-i', inJar.canonicalPath,
                '-o', tmp.toFile().canonicalPath,
                '-m', inSrg.canonicalPath,
                '--access-transformer', inAt.canonicalPath})
        Path tmpSrg
        copy(tmp, outJar.toPath(), includes, excludes)
        Files.delete(tmp)
        if (tmpSrg) Files.delete(tmpSrg)

        def tmpApi = Files.createTempFile("banner", "jar")
        SpecialSource.main(new String[]{
                '-i', inApiJar.canonicalPath,
                '-o', tmpApi.toFile().canonicalPath,
                '-m', inSrg.canonicalPath,
                '--access-transformer', inApiAt.canonicalPath})
        Path tmpSrgApi
        copy(tmpApi, outApiJar.toPath(), includesApi, excludesApi)
        Files.delete(tmpApi)
        if (tmpSrgApi) Files.delete(tmpSrgApi)
    }

    private static void copy(Path inJar, Path outJar, List<String> includes, List<String> excludes) {
        def fileIn = new JarFile(inJar.toFile())
        def entries = fileIn.entries().collect { it.name }
        entries.removeIf { name ->
            !(includes.any { name.startsWith(it) } && !excludes.any { name.startsWith(it) })
        }
        com.mohistmc.banner.gradle.Utils.using(new JarOutputStream(new FileOutputStream(outJar.toFile()))) { out ->
            entries.each { entry ->
                out.putNextEntry(new JarEntry(entry))
                def is = fileIn.getInputStream(new JarEntry(entry))
                com.mohistmc.banner.gradle.Utils.write(is, out)
                is.close()
            }
        }
        fileIn.close()
    }

    @InputFile
    File getInJar() {
        return inJar
    }

    void setInJar(File inJar) {
        this.inJar = inJar
    }

    @InputFile
    File getInApiJar() {
        return inApiJar
    }

    void setInApiJar(File inJar) {
        this.inApiJar = inJar
    }

    @Input
    List<String> getIncludes() {
        return includes
    }

    void setIncludes(List<String> includes) {
        this.includes = includes
    }

    @Input
    List<String> getExcludes() {
        return excludes
    }

    void setExcludes(List<String> excludes) {
        this.excludes = excludes
    }

    @InputFile
    File getSsJar() {
        return ssJar
    }

    void setSsJar(File ssJar) {
        this.ssJar = ssJar
    }

    @OutputFile
    File getOutJar() {
        return outJar
    }

    void setOutJar(File outJar) {
        this.outJar = outJar
    }

    @OutputFile
    File getOutApiJar() {
        return outApiJar
    }

    void setOutApiJar(File outJar) {
        this.outApiJar = outJar
    }

    @OutputFile
    File getOutDeobf() {
        return outDeobf
    }

    void setOutDeobf(File outDeobf) {
        this.outDeobf = outDeobf
    }

    @Input
    @Optional
    String getBukkitVersion() {
        return bukkitVersion
    }

    void setBukkitVersion(String bukkitVersion) {
        this.bukkitVersion = bukkitVersion
    }
}
