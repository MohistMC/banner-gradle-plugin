package com.mohistmc.banner.gradle.tasks

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.lorenztiny.TinyMappingsReader
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.md_5.specialsource.InheritanceMap
import net.md_5.specialsource.Jar
import net.md_5.specialsource.provider.JarProvider
import org.cadixdev.bombe.type.MethodDescriptor
import org.cadixdev.bombe.type.ObjectType
import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.io.srg.SrgWriter
import org.cadixdev.lorenz.io.srg.csrg.CSrgReader
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader
import org.cadixdev.lorenz.io.srg.tsrg.TSrgWriter
import org.cadixdev.lorenz.model.ClassMapping
import org.cadixdev.lorenz.model.FieldMapping
import org.cadixdev.lorenz.model.TopLevelClassMapping
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.objectweb.asm.Type

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors

class ProcessMappingV2Task extends DefaultTask {

    private File buildData
    private File inJar
    private File inVanillaJar
    private String mcVersion
    private String bukkitVersion
    private File outDir
    private String packageName

    @TaskAction
    void create() {
        def tree = new MemoryMappingTree()
        MappingReader.read(LoomGradleExtension.get(project).mappingConfiguration.tinyMappings, tree)
        def official = new TinyMappingsReader(tree, "official", "named").read()
        def mcp = new TinyMappingsReader(tree, "named", "intermediary").read()

        if (!outDir.isDirectory()) {
            outDir.mkdirs()
        }
        new File(outDir, "srg_to_named.srg").withWriter {
            new SrgWriter(it) {
                @Override
                void write(final MappingSet mappings) {
                    mappings.getTopLevelClassMappings().stream()
                            .filter { !it.hasMappings() }
                            .sorted(this.getConfig().getClassMappingComparator())
                            .forEach(this::writeClassMapping)
                    super.write(mappings)
                }

                @Override
                protected void writeClassMapping(final ClassMapping<?, ?> mapping) {
                    if (!mapping.hasDeobfuscatedName()) {
                        this.writer.println(String.format("CL: %s %s", mapping.getFullObfuscatedName(), mapping.getFullDeobfuscatedName()))
                    }
                    super.writeClassMapping(mapping)
                }
            }.write(new TinyMappingsReader(tree, "intermediary", "named").read())
        }
        new File(outDir, 'obf_to_intermediary.srg').withWriter {
            new TSrgWriter(it) {
                @Override
                protected void writeFieldMapping(FieldMapping mapping) {
                    this.writer.println(String.format("\t%s %s",
                            mapping.getObfuscatedName(),
                            mapping.getDeobfuscatedName()))
                }
            }.write(new TinyMappingsReader(tree, "official", "intermediary").read())
        }

        def srg = MappingSet.create()
        File srgIntermediary = new File(outDir, "obf_to_intermediary.srg")
        srgIntermediary.toPath().toFile().withReader {
            def data = it.lines().filter { String s -> !(s.startsWith('\t\t') || s.startsWith('tsrg2')) }.collect(Collectors.joining('\n'))
            new TSrgReader(new StringReader(data.toString())).read(srg)
        }
        def csrg = MappingSet.create()
        def clFile = new File(buildData, "mappings/bukkit-$mcVersion-cl.csrg")
        clFile.withReader {
            new CSrgReader(it).read(csrg)
        }
        def srgRev = srg.reverse()
        def finalMap = srgRev.merge(csrg).reverse()
        new File(outDir, 'inheritanceMap.txt').with {
            it.delete()
            it.createNewFile()
            def im = new InheritanceMap()
            def classes = []
            csrg.topLevelClassMappings.each {
                classes.add(it.fullDeobfuscatedName)
                it.innerClassMappings.each {
                    classes.add(it.fullDeobfuscatedName)
                }
            }
            im.generate(new JarProvider(Jar.init(this.inJar)), classes)
            it.withPrintWriter {
                for (def className : classes) {
                    def parents = im.getParents(className).collect { finalMap.getOrCreateClassMapping(it).fullDeobfuscatedName }
                    if (!parents.isEmpty()) {
                        it.print(finalMap.getOrCreateClassMapping(className).fullDeobfuscatedName)
                        it.print(' ')
                        it.println(parents.join(' '))
                    }
                }
            }
        }
        new File(outDir, 'bukkit_srg.srg').withWriter {
            new TSrgWriter(it) {
                @Override
                void write(final MappingSet mappings) {
                    mappings.getTopLevelClassMappings().stream()
                            .sorted(this.getConfig().getClassMappingComparator())
                            .forEach(this::writeClassMapping)
                }

                @Override
                protected void writeClassMapping(ClassMapping<?, ?> mapping) {
                    if (!mapping.hasMappings()) {
                        this.writer.println(String.format("%s %s", mapping.getFullObfuscatedName(), mapping.getFullDeobfuscatedName()));
                    } else if (mapping.fullObfuscatedName.contains('/')) {
                        super.writeClassMapping(mapping)
                    }
                }

                @Override
                protected void writeFieldMapping(FieldMapping mapping) {
                    def cl = srgRev.getClassMapping(mapping.parent.fullDeobfuscatedName).get()
                    def field = cl.getFieldMapping(mapping.deobfuscatedName).get().deobfuscatedName
                    def nmsCl = official.getClassMapping(cl.fullDeobfuscatedName)
                            .get().getFieldMapping(field).get().signature.type.get()
                    def sig = Type.getType(csrg.deobfuscate(nmsCl).toString()).getClassName()
                    this.writer.println(String.format("    %s %s -> %s", sig, mapping.getDeobfuscatedName(), mapping.getObfuscatedName()))
                }
            }.write(finalMap)
        }
        new File(outDir, 'bukkit_at.at').withWriter { w ->
            new File(buildData, "mappings/bukkit-${mcVersion}.at").eachLine { l ->
                if (l.trim().isEmpty() || l.startsWith('#')) {
                    w.writeLine(l)
                    return
                }
                def split = l.split(' ', 2)
                def i = split[1].indexOf('(')
                if (i == -1) {
                    def name = split[1].substring(split[1].lastIndexOf('/') + 1)
                    if (name.charAt(0).isUpperCase() && name.charAt(1).isLowerCase()) {
                        w.writeLine("${split[0].replace('inal', '')} ${(finalMap.deobfuscate(new ObjectType(split[1])) as ObjectType).className.replace('/', '.')}")
                    } else {
                        def cl = split[1].substring(0, split[1].lastIndexOf('/'))
                        def f = finalMap.getClassMapping(cl)
                                .flatMap { mcp.getClassMapping(it.fullDeobfuscatedName) }
                                .flatMap { it.getFieldMapping(name) }
                                .map { it.deobfuscatedName }
                        if (f.isEmpty()) {
                            w.writeLine("# TODO ${split[0].replace('inal', '')} ${(finalMap.deobfuscate(new ObjectType(cl)) as ObjectType).className.replace('/', '.')} $name")
                        } else {
                            w.writeLine("${split[0].replace('inal', '')} ${(finalMap.deobfuscate(new ObjectType(cl)) as ObjectType).className.replace('/', '.')} ${f.get()}")
                        }
                    }
                } else {
                    def desc = split[1].substring(i)
                    def s = split[1].substring(0, i)
                    def cl = s.substring(0, s.lastIndexOf('/'))
                    def name = s.substring(s.lastIndexOf('/') + 1)
                    def m = finalMap.getClassMapping(cl)
                            .flatMap { mcp.getClassMapping(it.fullDeobfuscatedName) }
                            .map() { it.methodMappings.find { it.obfuscatedName == name } }
                    if (m.isEmpty()) {
                        w.writeLine("${name == '<init>' ? '' : '# TODO '}${split[0].replace('inal', '')} ${(finalMap.deobfuscate(new ObjectType(cl)) as ObjectType).className.replace('/', '.')} $name${finalMap.deobfuscate(MethodDescriptor.of(desc))}")
                    } else {
                        w.writeLine("${split[0].replace('inal', '')} ${(finalMap.deobfuscate(new ObjectType(cl)) as ObjectType).className.replace('/', '.')} ${m.get().deobfuscatedName}${m.get().deobfuscatedDescriptor}")
                    }
                }
            }
        }

        var srgFile = project.file("${project.buildDir}/banner_cache/tmp_srg/bukkit_srg.srg")
        var outSrgFile = project.file("${project.rootDir}/src/main/resources/mappings/spigot2srg.srg")
        if (srgFile.exists()) {
            copy(srgFile.toPath(), outSrgFile.toPath())
        }

        var inheritanceMapFile = project.file("${project.buildDir}/banner_cache/tmp_srg/inheritanceMap.txt")
        var outInheritanceMapFile = project.file("${project.rootDir}/src/main/resources/mappings/inheritanceMap.txt")
        if (inheritanceMapFile.exists()) {
            copy(inheritanceMapFile.toPath(), outInheritanceMapFile.toPath())
        }
    }

    private static void copy(Path sourceFile, Path outFile) {
        try {
            if (outFile.toFile().exists()) {
                outFile.toFile().delete()
            }
            Files.copy(new FileInputStream(sourceFile.toFile()), outFile, StandardCopyOption.REPLACE_EXISTING)
            println("Successfully applied moving files!")
        } catch (IOException e) {
            println("Error：${e.message}")
        }
    }

    private static void innerClasses(MappingSet csrg, MappingSet srg) {
        srg.topLevelClassMappings.stream().filter { !csrg.hasTopLevelClassMapping(it.fullObfuscatedName) }
                .filter { TopLevelClassMapping mapping -> csrg.topLevelClassMappings.findResult { it.fullObfuscatedName.startsWith(mapping.fullObfuscatedName) } != null }
                .forEach {
                    name(it.fullObfuscatedName, '', csrg)
                }
    }

    private static void name(String cl, String suffix, MappingSet csrg) {
        def m = csrg.getTopLevelClassMapping(cl)
        if (m.isPresent()) {
            csrg.createTopLevelClassMapping(cl + suffix, m.get().fullObfuscatedName + suffix)
        } else {
            def i = cl.lastIndexOf('$')
            if (i <= 0) return
            def outer = cl.substring(0, i)
            name(outer, cl.substring(i) + suffix, csrg)
        }
    }

    @InputDirectory
    File getBuildData() {
        return buildData
    }

    void setBuildData(File buildData) {
        this.buildData = buildData
    }

    @InputFile
    File getInJar() {
        return inJar
    }

    void setInJar(File inJar) {
        this.inJar = inJar
    }

    @InputFile
    File getInVanillaJar() {
        return inVanillaJar
    }

    void setInVanillaJar(File inVanillaJar) {
        this.inVanillaJar = inVanillaJar
    }

    @Input
    String getMcVersion() {
        return mcVersion
    }

    void setMcVersion(String mcVersion) {
        this.mcVersion = mcVersion
    }

    @Input
    String getBukkitVersion() {
        return bukkitVersion
    }

    void setBukkitVersion(String bukkitVersion) {
        this.bukkitVersion = bukkitVersion
    }

    @OutputDirectory
    File getOutDir() {
        return outDir
    }

    void setOutDir(File outDir) {
        this.outDir = outDir
    }

    @Input
    String getPackageName() {
        return packageName
    }

    void setPackageName(String packageName) {
        this.packageName = packageName
    }
}
