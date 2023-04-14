package com.github.ordom.remapper.mapping

import com.github.ordom.remapper.INTERMEDIARY
import com.github.ordom.remapper.OFFICIAL_OBSFUCATED
import com.github.ordom.remapper.SEARGE
import com.github.ordom.remapper.mapping.tsrg.SignatureTranslator
import com.github.ordom.remapper.mapping.tsrg.TsrgTree
import net.fabricmc.mapping.reader.v2.TinyMetadata
import net.fabricmc.mapping.tree.TinyTree
import java.nio.file.Path

val MERGED_METADATA = object: TinyMetadata {
    override fun getMajorVersion() = 2
    override fun getMinorVersion() = 0
    override fun getNamespaces() = listOf(INTERMEDIARY, SEARGE)
    override fun getProperties() = mutableMapOf<String, String?>()
}

class IntermediaryToSrg(
    private val intermediary: Intermediary,
    private val srg: Srg
) {
    constructor(path: Path, version: String): this(Intermediary(path, version), Srg(path, version))
    @Suppress("NAME_SHADOWING")
    fun merge(): TinyTree {
        intermediary.download()
        srg.download()
        val intermediaryTree = intermediary.load()
        val srgTree = srg.load()
        val mergedTree = TsrgTree()
        mergedTree.metadata = MERGED_METADATA
        mergedTree.from = INTERMEDIARY
        mergedTree.to = SEARGE
        val translator = SignatureTranslator(mergedTree)
        intermediaryTree.classes.forEach {
            val obfuscated = it.getName(OFFICIAL_OBSFUCATED)
            val intermediary = it.getName(INTERMEDIARY)
            val srgClassDef = srgTree.defaultNamespaceClassMap[obfuscated]!!
            val classEntry = TsrgTree.ClassImpl(intermediary, srgClassDef.getName(SEARGE), MERGED_METADATA::index)
            it.fields.forEach { field ->
                val obfuscated = field.getName(OFFICIAL_OBSFUCATED)
                val intermediary = field.getName(INTERMEDIARY)
                val srgFieldDef = srgClassDef.fields.first { it.getName(OFFICIAL_OBSFUCATED) == obfuscated }
                classEntry.fields.add(
                    TsrgTree.FieldImpl(
                        obfuscated = intermediary, srgFieldDef.getName(SEARGE),
                        descriptor = field.getDescriptor(INTERMEDIARY),
                        translator = translator,
                        namespaceMapping = MERGED_METADATA::index
                    )
                )
            }
            it.methods.forEach { method ->
                val obfuscated = method.getName(OFFICIAL_OBSFUCATED)
                val intermediary = method.getName(INTERMEDIARY)
                val srgMethodDef = srgClassDef.methods.first { it.getName(OFFICIAL_OBSFUCATED) == obfuscated }
                val methodEntry = TsrgTree.MethodImpl(intermediary, srgMethodDef.getName(SEARGE), method.getDescriptor(INTERMEDIARY), translator, MERGED_METADATA::index)
                classEntry.methods.add(methodEntry)
            }
            mergedTree.classes.add(classEntry)
            mergedTree.defaultNamespaceClassMap[classEntry.getName(INTERMEDIARY)] = classEntry
        }
        return mergedTree
    }
}
