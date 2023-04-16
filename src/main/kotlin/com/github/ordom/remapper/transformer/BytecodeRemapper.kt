package com.github.ordom.remapper.transformer

import com.github.ordom.remapper.CLASS_MAPPED_SEARGE
import com.github.ordom.remapper.INTERMEDIARY
import com.github.ordom.remapper.OFFICIAL_OBFUSCATED
import com.github.ordom.remapper.VERSION
import com.github.ordom.remapper.mapping.IntermediaryToSrg
import com.github.ordom.remapper.mapping.tsrg.SignatureTranslator
import com.github.ordom.remapper.metadata.FabricMetadata
import com.github.ordom.remapper.metadata.ForgeMetadata
import com.github.ordom.remapper.metadata.ForgePackMeta
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loom.util.TinyRemapperMappingsHelper
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

private typealias strM<T> = Map<String, T>
val MIXIN_CONFIGS = Attributes.Name("MixinConfigs")

@Serializable
private data class FabricRefMap(
    val mappings: strM<strM<String>>? = null,
    val data: strM<strM<strM<String>>>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
private val JSON = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

class BytecodeRemapper(
    private val i2s: IntermediaryToSrg
) {
    private val mappedClientPath = i2s.mojang.mappedClientPath
    private val mapping = i2s.merge()
    val remapper: TinyRemapper = TinyRemapper.newRemapper()
        .withMappings(TinyRemapperMappingsHelper.create(mapping, INTERMEDIARY, CLASS_MAPPED_SEARGE, false))
        .build()

    @OptIn(ExperimentalPathApi::class)
    fun remap(outputPath: Path, jarFile: Path, deleteTmp: Boolean = true): Path {
        val path = outputPath / "${jarFile.nameWithoutExtension}-remapped"
        outputPath.createDirectories()
        path.deleteRecursively()

        if (!mappedClientPath.exists()) {
            val mcRemapper = TinyRemapper.newRemapper()
                .withMappings(TinyRemapperMappingsHelper.create(i2s.intermediary.load(), OFFICIAL_OBFUSCATED, INTERMEDIARY, false))
                .build()
            val mcTag = mcRemapper.createInputTag()
            mcRemapper.readInputs(i2s.mojang.clientPath)
            val mcOutput = OutputConsumerPath.Builder(mappedClientPath)
                .build()
            mcRemapper.apply(mcOutput, mcTag)
            i2s.mojang.clientPath.deleteIfExists()
        }
        val translator = SignatureTranslator(mapping)
        val output = OutputConsumerPath.Builder(path)
            .build()
        val moaTag = remapper.createInputTag()
        remapper.readClassPath(mappedClientPath)
        remapper.readInputs(moaTag, jarFile)
        remapper.apply(output, moaTag)

        output.addNonClassFiles(jarFile, NonClassCopyMode.FIX_META_INF, remapper)
        val fabricMetadata = FabricMetadata.fromJson(path / "fabric.mod.json")
        // remap mixins classes
        fabricMetadata.mixins.asSequence()
            .map { FabricMetadata.MixinConfig.fromJson(path / it.config) }
            .map { (path / it.refMap).toFile() }
            .map { it to it.readText() }
            .mapValues { JSON.decodeFromString<FabricRefMap>(it) }
            .mapValues { mapToSrg(it, translator) }
            .mapValues { JSON.encodeToString(it) }
            .forEach { it.first.writeText(it.second) }

        // remove nested jars
        fabricMetadata.jars.map { path / it.file }.forEach(Path::deleteIfExists)

        // generate forge meta
        val forgePackMeta = ForgePackMeta(ForgePackMeta.PackMeta.MC1194) // todo: detect version
        path / "pack.mcmeta" writeText forgePackMeta.toString()
        // generate forge toml
        val forgeMetadata = ForgeMetadata(
            // '-' is allowed in fabric mod id, but not in forge
            modid = fabricMetadata.id.replace('-', '_'),
            version = fabricMetadata.version,
            name = fabricMetadata.name,
            description = fabricMetadata.description ?: "",
            license = fabricMetadata.license ?: "All rights reserved (Unlicensed)",
        )
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
        val manifest = Manifest((path / "META-INF" / "MANIFEST.MF").inputStream())
        manifest.mainAttributes[MIXIN_CONFIGS] = fabricMetadata.mixins.joinToString(",")
        manifest.mainAttributes[Attributes.Name.SPECIFICATION_TITLE] = forgeMetadata.modid
        manifest.mainAttributes[Attributes.Name.SPECIFICATION_VERSION] = "1"
        manifest.mainAttributes[Attributes.Name.IMPLEMENTATION_TITLE] = forgeMetadata.name
        manifest.mainAttributes[Attributes.Name.IMPLEMENTATION_VERSION] = forgeMetadata.version
        manifest.mainAttributes[Attributes.Name("Mod-Translator")] = "Ordom"
        manifest.mainAttributes[Attributes.Name("X-Ordomal-Remapped")] = "true"
        manifest.mainAttributes[Attributes.Name("X-Ordomal-Origin-Mod")] = jarFile.name
        manifest.mainAttributes[Attributes.Name("X-Ordomal-Version")] = VERSION
        manifest.write((path / "META-INF" / "MANIFEST.MF").outputStream())

        // generate forge mod class
        val asm = ClassWriter(0).apply {
            val classSig = "ordom/remapped/${forgeMetadata.modid}/${forgeMetadata.modid}"
            visit(61, Opcodes.ACC_PUBLIC, classSig, null, "java/lang/Object", null)
            visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true).visit("value", forgeMetadata.modid)
            newMethod(classSig, "<init>", "()V", false)
            visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                visitCode()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(1, 1)
                visitEnd()
            }
            visitEnd()
        }
        (path / "ordom" / "remapped" / forgeMetadata.modid).createDirectories()
        (path / "ordom" / "remapped" / forgeMetadata.modid / "${forgeMetadata.modid}.class").writeBytes(asm.toByteArray())

        // delete fabric meta
        (path / "fabric.mod.json").deleteIfExists()

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
        if (deleteTmp) path.deleteRecursively()
        return path
    }

    private fun encode(s: String): String =
        s.map {
            if (it.code in 0..31 || it.code >= 127) "\\u${it.code.toString(16).padStart(4, '0')}"
            else it
        }.joinToString("")

    private fun mapToSrg(mixinRefMap: FabricRefMap, translator: SignatureTranslator): FabricRefMap {
        val mappings = mixinRefMap.mappings?.map { (mixinClass, map) ->
            mixinClass to map.map { (mixinRef, inter) ->
                val newInter = translator.translate(inter, CLASS_MAPPED_SEARGE)
                mixinRef to newInter
            }.toMap()
        }?.toMap()
        val data = mixinRefMap.data?.map { (namespace, map1) ->
            if (namespace != "named:intermediary")
                error("Unknown namespace key: $namespace")
            namespace to map1.map { (mixinClass, map2) ->
                val newMap = map2.map { (mixinRef, inter) ->
                    mixinRef to translator.translate(inter, CLASS_MAPPED_SEARGE)
                }.toMap()
                mixinClass to newMap
            }.toMap()
        }?.toMap()
        return mixinRefMap.copy(mappings = mappings, data = data)
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
