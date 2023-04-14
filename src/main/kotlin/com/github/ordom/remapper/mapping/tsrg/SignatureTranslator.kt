package com.github.ordom.remapper.mapping.tsrg

import net.fabricmc.mapping.tree.TinyTree
import org.slf4j.LoggerFactory

private val IntRange.length: Int
    get() = last - first + 1

private val LOGGER = LoggerFactory.getLogger("SignatureTranslator")

class SignatureTranslator(
    private val mapping: TinyTree
) {
    val regex = Regex("L([^;]+);")
    val jvmClassSignature = Regex("(\\[*(L[^;]+;))|([ZCBSIFJDV])")

    /**
     * Can only translate class signatures, without check.
     */
    fun translate(signature: String, to: String): String {
        // if this is a method signature, translate the return type and parameters
        mapMethodWithOwner(signature, to)?.let { return it }

        val matches = regex.findAll(signature)
        var ret = signature
        matches.forEach {
            val from = it.groupValues[1]
            val def = mapping.defaultNamespaceClassMap[from] ?: return@forEach // skip non-mapped classes
            ret = ret.replace(from, def.getName(to))
        }
        return ret
    }

    /**
     * @return null if no method mapping found, or the signature of the method
     */
    fun mapMethodWithOwner(signature: String, to: String): String? {
        val defaultNamespace = mapping.metadata.namespaces[0]
        val ownerMatch = jvmClassSignature.matchAt(signature, 0) ?: return null
        if (ownerMatch.range.first != 0) return null
        val owner = ownerMatch.value
        val ownerQ = owner.drop(1).dropLast(1)
        val method = signature.substring(owner.length, signature.indexOf('('))
        val parameters = signature.substring(signature.indexOf('(') + 1, signature.indexOf(')'))
        val returnType = signature.substring(signature.indexOf(')') + 1)
        val ownerDef = mapping.defaultNamespaceClassMap[ownerQ] ?: return null
        val methodDef = mapping.classes.flatMap { it.methods }.filter {
            it.getName(defaultNamespace) == method && it.getDescriptor(defaultNamespace) == "($parameters)$returnType"
        }.let {
            if (it.size == 1) it[0] else {
                if (it.size > 1) {
                    LOGGER.error("Found multiple methods with the same name and descriptor: $it")
                    error("Found multiple methods with the same name and descriptor: $it")
                }
                return null
            }
        }
        return "L${ownerDef.getName(to)};${methodDef.getName(to)}${translate("($parameters)$returnType", to)}"
    }
}
