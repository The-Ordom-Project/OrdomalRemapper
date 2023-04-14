package com.github.ordom.remapper.mapping.tsrg

import net.fabricmc.mapping.tree.TinyTree

private val IntRange.length: Int
    get() = last - first + 1

class SignatureTranslator(
    private val mapping: TinyTree
) {
    val regex = Regex("L([^;]+);")
    val jvmClassSignature = Regex("(\\[*(L[^;]+;))|([ZCBSIFJDV])")

    /**
     * Can only translate class signatures, without check.
     */
    fun translate(signature: String, to: String): String {
        val matches = regex.findAll(signature)
        var ret = signature
        matches.forEach {
            val from = it.groupValues[1]
            val def = mapping.defaultNamespaceClassMap[from] ?: return@forEach // skip non-mapped classes
            ret = ret.replace(from, def.getName(to))
        }
        return ret
    }
}
