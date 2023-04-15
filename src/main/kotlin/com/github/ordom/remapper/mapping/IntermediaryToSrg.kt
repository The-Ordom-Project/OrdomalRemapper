package com.github.ordom.remapper.mapping

import com.github.ordom.remapper.*
import com.github.ordom.remapper.mapping.tsrg.SignatureTranslator
import com.github.ordom.remapper.mapping.tsrg.TsrgTree
import net.fabricmc.mapping.reader.v2.TinyMetadata
import net.fabricmc.mapping.tree.TinyTree
import java.nio.file.Path

val MERGED_METADATA = object: TinyMetadata {
    override fun getMajorVersion() = 2
    override fun getMinorVersion() = 0
    override fun getNamespaces() = listOf(INTERMEDIARY, CLASS_MAPPED_SEARGE, OFFICIAL_OBFUSCATED)
    override fun getProperties() = mutableMapOf<String, String?>()
}

class IntermediaryToSrg(
    val intermediary: Intermediary,
    val srg: Srg,
    val mojang: Mojang
) {
    constructor(path: Path, version: String): this(Intermediary(path, version), Srg(path, version), Mojang(path, version))
    @Suppress("NAME_SHADOWING")
    fun merge(): TsrgTree {
        intermediary.download()
        srg.download()
        mojang.download()
        val intermediaryTree = intermediary.load()
        val srgTree = srg.load()
        val mergedTree = TsrgTree(MERGED_METADATA)
        val mojClassMap = mojang.load().defaultNamespaceClassMap
        mergedTree.from = INTERMEDIARY
        mergedTree.to = CLASS_MAPPED_SEARGE
        val translator = SignatureTranslator(mergedTree)
        intermediaryTree.classes.forEach {
            val obfuscated = it.getName(OFFICIAL_OBFUSCATED)
            val intermediary = it.getName(INTERMEDIARY)
            val srgClassDef = srgTree.defaultNamespaceClassMap[obfuscated]!!
            val classEntry = TsrgTree.ClassImpl(
                arrayOf(
                    intermediary,
                    mojClassMap[obfuscated]!!.getName(SEARGE), // well, this is the mojang name,
                    // but it is marked as searge
                    obfuscated
                ),
                MERGED_METADATA::index
            )
            it.fields.forEach { field ->
                val obfuscated = field.getName(OFFICIAL_OBFUSCATED)
                field.getDescriptor(OFFICIAL_OBFUSCATED)
                val intermediary = field.getName(INTERMEDIARY)
                val srgFieldDef = srgClassDef.fields.first {
                    // srg has no descriptor for fields, just don't check it
                    it.getName(OFFICIAL_OBFUSCATED) == obfuscated
                }
                classEntry.fields.add(
                    TsrgTree.FieldImpl(
                        names = arrayOf(intermediary, srgFieldDef.getName(SEARGE), obfuscated),
                        descriptor = field.getDescriptor(INTERMEDIARY),
                        translator = translator,
                        namespaceMapping = MERGED_METADATA::index
                    )
                )
            }
            it.methods.forEach { method ->
                val obfuscated = method.getName(OFFICIAL_OBFUSCATED)
                val descriptor = method.getDescriptor(OFFICIAL_OBFUSCATED)
                val intermediary = method.getName(INTERMEDIARY)
                val srgMethodDef = srgClassDef.methods.first {
                    it.getName(OFFICIAL_OBFUSCATED) == obfuscated && it.getDescriptor(OFFICIAL_OBFUSCATED) == descriptor
                }
                val methodEntry = TsrgTree.MethodImpl(
                    names = arrayOf(intermediary, srgMethodDef.getName(SEARGE), obfuscated),
                    descriptor = method.getDescriptor(INTERMEDIARY),
                    translator = translator,
                    namespaceMapping = MERGED_METADATA::index
                )
                classEntry.methods.add(methodEntry)
            }
            mergedTree.classes.add(classEntry)
            mergedTree.defaultNamespaceClassMap[classEntry.getName(INTERMEDIARY)] = classEntry
        }
        return mergedTree
    }
}
