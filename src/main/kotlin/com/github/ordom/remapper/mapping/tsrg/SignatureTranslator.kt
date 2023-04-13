package com.github.ordom.remapper.mapping.tsrg

import net.fabricmc.mapping.tree.TinyTree

class SignatureTranslator(
    private val mapping: TinyTree
) {
    val regex = Regex("L([^;]+);")
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
