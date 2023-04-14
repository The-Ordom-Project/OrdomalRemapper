package com.github.ordom.remapper.transformer

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
        FabricMetadata.fromJson(path / "fabric.mod.json").mixins.asSequence()
            .map { FabricMetadata.MixinConfig.fromJson(path / it) }
            .map { (path / it.refMap).toFile() }
            .map { it to it.readText() }
            .mapValues { JSON.decodeFromString(FabricRefMap.serializer(), it) }
            .mapValues {
                it.mapping?.forEach { (_, map) ->
                    map.keys.forEach { s ->
                        map[s] = translator.translate(map[s]!!, "srg")
                    }
                }
                it.data?.forEach { (_, map1) ->
                    map1.forEach { (_, map2) ->
                        map2.keys.forEach { s ->
                            map2[s] = translator.translate(map2[s]!!, "srg")
                        }
                    }
                }
                it
            }.mapValues {
                JSON.encodeToString(FabricRefMap.serializer(), it)
            }.forEach { it.first.writeText(it.second) }
        // generate forge meta
        val forgePackMeta = ForgePackMeta(ForgePackMeta.PackMeta.MC1194) // todo: detect version
        path / "pack.mcmeta" writeText forgePackMeta.toString()
        // generate forge toml
        val forgeMetadata = ForgeMetadata()
        path / "META-INF" / "mods.toml" writeText """
            modLoader = "javafml"
            loaderVersion = "[45,)"
            license = "LGPL-2.1"
            [[mods]]
            modId = "ordom_forge"
            version = "0.1"
            displayName = "OrdomForge"
            description = "Generated from fabric"
        """.trimIndent()
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
