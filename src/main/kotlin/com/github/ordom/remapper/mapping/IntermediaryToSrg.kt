package com.github.ordom.remapper.mapping

import com.github.ordom.remapper.mapping.tsrg.SignatureTranslator
import com.github.ordom.remapper.mapping.tsrg.TsrgTree
import net.fabricmc.mapping.reader.v2.TinyMetadata
import net.fabricmc.mapping.tree.TinyTree
import java.nio.file.Path

val MERGED_METADATA = object: TinyMetadata {
    override fun getMajorVersion() = 2
    override fun getMinorVersion() = 0
    override fun getNamespaces() = listOf("intermediary", "srg")
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
        mergedTree.from = "intermediary"
        mergedTree.to = "srg"
        val translator = SignatureTranslator(mergedTree)
        intermediaryTree.classes.forEach {
            val obfuscated = it.getName("official")
            val intermediary = it.getName("intermediary")
            val srgClassDef = srgTree.defaultNamespaceClassMap[obfuscated]!!
            val classEntry = TsrgTree.ClassImpl(intermediary, srgClassDef.getName("srg"), MERGED_METADATA::index)
            it.fields.forEach { field ->
                val obfuscated = field.getName("official")
                val intermediary = field.getName("intermediary")
                val srgFieldDef = srgClassDef.fields.first { it.getName("official") == obfuscated }
                classEntry.fields.add(
                    TsrgTree.FieldImpl(
                        obfuscated = intermediary, srgFieldDef.getName("srg"),
                        descriptor = field.getDescriptor("intermediary"),
                        translator = translator,
                        namespaceMapping = MERGED_METADATA::index
                    )
                )
            }
            it.methods.forEach { method ->
                val obfuscated = method.getName("official")
                val intermediary = method.getName("intermediary")
                val srgMethodDef = srgClassDef.methods.first { it.getName("official") == obfuscated }
                val methodEntry = TsrgTree.MethodImpl(intermediary, srgMethodDef.getName("srg"), method.getDescriptor("intermediary"), translator, MERGED_METADATA::index)
                classEntry.methods.add(methodEntry)
            }
            mergedTree.classes.add(classEntry)
            mergedTree.defaultNamespaceClassMap[classEntry.getName("intermediary")] = classEntry
        }
        return mergedTree
    }
}
