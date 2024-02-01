package com.mohistmc.banner.gradle.tasks


import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

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
    }

    @TaskAction
    void remap() {
        copy(inJar.toPath(), outJar.toPath(), includes, excludes)
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
