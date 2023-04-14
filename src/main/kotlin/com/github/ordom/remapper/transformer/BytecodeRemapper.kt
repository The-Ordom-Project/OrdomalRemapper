package com.github.ordom.remapper.transformer

import com.github.ordom.remapper.VERSION
import com.github.ordom.remapper.mapping.IntermediaryToSrg
import com.github.ordom.remapper.mapping.tsrg.SignatureTranslator
import com.github.ordom.remapper.metadata.FabricMetadata
import com.github.ordom.remapper.metadata.ForgeMetadata
import com.github.ordom.remapper.metadata.ForgePackMeta
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.loom.util.TinyRemapperMappingsHelper
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

private typealias strM<T> = MutableMap<String, T>

@Serializable
private data class FabricRefMap(
    val mapping: strM<strM<String>>? = null,
    val data: strM<strM<strM<String>>>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
private val JSON = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

class BytecodeRemapper(
    i2s: IntermediaryToSrg
) {
    private val mapping = i2s.merge()
    val remapper: TinyRemapper = TinyRemapper.newRemapper()
        .withMappings(TinyRemapperMappingsHelper.create(mapping, "intermediary", "srg", false))
        .build()

    @OptIn(ExperimentalPathApi::class)
    fun remap(jarFile: Path, deleteTmp: Boolean = true): Path {
        val path = Path.of("run", "out", "${jarFile.nameWithoutExtension}-remapped")
        path.parent.createDirectories()
        path.deleteRecursively()

        val translator = SignatureTranslator(mapping)
        val output = OutputConsumerPath.Builder(path)
            .build()
        remapper.readInputs(jarFile)
        remapper.apply(output)

        output.addNonClassFiles(jarFile, NonClassCopyMode.FIX_META_INF, remapper)
        // remap mixins classes
        val mixinConfigs = FabricMetadata.fromJson(path / "fabric.mod.json").mixins
        mixinConfigs.asSequence()
            .map { FabricMetadata.MixinConfig.fromJson(path / it) }
            .map { (path / it.refMap).toFile() }
            .map { it to it.readText() }
            .mapValues { JSON.decodeFromString(FabricRefMap.serializer(), it) }
            .mapValues { mapToSrg(it, translator) }
            .mapValues { JSON.encodeToString(FabricRefMap.serializer(), it) }
            .forEach { it.first.writeText(it.second) }

        // generate forge meta
        val forgePackMeta = ForgePackMeta(ForgePackMeta.PackMeta.MC1194) // todo: detect version
        path / "pack.mcmeta" writeText forgePackMeta.toString()
        // generate forge toml
        val forgeMetadata = ForgeMetadata(
            modid = "ordom_forge",
            version = "0.1",
            name = "OrdomForge",
            description = "Generated from fabric",
            license = "LGPL-2.1",
        ) // todo: parse from fabric.mod.json
        path / "META-INF" / "mods.toml" writeText """
            modLoader = "javafml"
            loaderVersion = "[45,)"
            license = "${forgeMetadata.license}"
            [[mods]]
            modId = "${forgeMetadata.modid}"
            version = "${forgeMetadata.version}"
            displayName = "${forgeMetadata.name}"
            description = "${encode(forgeMetadata.description)}\nGenerated from fabric by OrdomRemapper"
        """.trimIndent()

        // aad info to manifest
        val manifest = (path / "META-INF" / "MANIFEST.MF").toFile()
        manifest.appendText("""
            MixinConfigs: ${mixinConfigs.joinToString(",")}
            Specification-Title: ${forgeMetadata.modid}
            Specification-Version: 1
            Implementation-Title: ${forgeMetadata.name}
            Implementation-Version: ${forgeMetadata.version}
            Mod-Translator: Ordom
            X-Ordomal-Remapped: true
            X-Ordomal-Origin-Mod: ${jarFile.name}
            X-Ordomal-Remapped-Mod: ${jarFile.nameWithoutExtension}-remapped.jar
            X-Ordomal-Remapped-Mod-Id: ordom_forge
            X-Ordomal-Remapped-Mod-Version: 0.1
            X-Ordomal-Version: $VERSION
        """.trimIndent())

        // generate forge mod class
        val asm = ClassWriter(0)
        asm.visit(61, Opcodes.ACC_PUBLIC, "ordom/remapped/ordom_forge", null, "java/lang/Object", null)
        asm.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true).visit("value", "ordom_forge")
        asm.newMethod("ordom/remapped/ordom_forge", "<init>", "()V", false)
        asm.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(1, 1)
            visitEnd()
        }
        asm.visitEnd()
        (path / "ordom" / "remapped").createDirectories()
        (path / "ordom" / "remapped" / "ordom_forge.class").writeBytes(asm.toByteArray())

        // pack into jar file
        val outputFile = path.resolveSibling("${path.name}.jar").toFile()
        outputFile.createNewFile()
        ZipOutputStream(outputFile.outputStream()).use { zipOutputStream ->
            path.walkTopDown().forEach {
                if (it.isDirectory()) return@forEach
                zipOutputStream.putNextEntry(ZipEntry(path.relativize(it).toString()))
                it.inputStream().copyTo(zipOutputStream)
                zipOutputStream.closeEntry()
            }
        }
        if (deleteTmp) {
            path.deleteRecursively()
        }
        return path
    }

    private fun encode(s: String): String =
        s.map {
            if (it.code in 0..31 || it.code >= 127) "\\u${it.code.toString(16).padStart(4, '0')}"
            else it
        }.joinToString("")

    private fun mapToSrg(mixinRefMap: FabricRefMap, translator: SignatureTranslator): FabricRefMap {
        val copy = mixinRefMap.copy()
        copy.mapping?.forEach { (_, map) ->
            map.keys.forEach { s ->
                map[s] = translator.translate(map[s]!!, "srg")
            }
        }
        copy.data?.forEach { (_, map1) ->
            map1.forEach { (_, map2) ->
                map2.keys.forEach { s ->
                    map2[s] = translator.translate(map2[s]!!, "srg")
                }
            }
        }
        return copy
    }
}

private infix fun Path.writeText(text: String) = this.toFile().writeText(text)

private fun Path.walkTopDown(): List<Path> {
    val list = mutableListOf<Path>()
    list.add(this)
    if (this.isDirectory()) {
        this.listDirectoryEntries().forEach {
            list.addAll(it.walkTopDown())
        }
    }
    return list
}

private fun <T, P, R> Sequence<Pair<T, P>>.mapValues(transform: (P) -> R) =
    map { it.first to transform(it.second) }
