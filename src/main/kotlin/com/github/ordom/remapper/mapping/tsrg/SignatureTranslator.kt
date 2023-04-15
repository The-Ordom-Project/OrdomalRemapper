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
    val jvmClassSignature = Regex("(\\[*(L[^;]+;))|[ZCBSIFJDV]")
    val methodDescriptor = Regex("^\\((\\[*L[^;]+;|[ZCBSIFJDV])*\\)(\\[*L[^;]+;|[ZCBSIFJDV])$")

    /**
     * Can only translate class signatures, without check.
     */
    fun translate(signature: String, to: String): String {
        // if this is a method signature, translate the return type and parameters
        mapMethodWithOwner(signature, to)?.let { return it }

        var ret = signature
        var matches = regex.find(ret)
        while (matches != null) {
            val from = matches.groupValues[1]
            val def = mapping.defaultNamespaceClassMap[from]
            if (def != null) { // skip non-mapped classes
                ret = ret.replaceRange(matches.groups[1]!!.range, def.getName(to))
            } else {
                if (from.startsWith("net/minecraft/")) {
                    LOGGER.warn("Found unmapped class in signature: $from")
                }
                matches = regex.find(ret, matches.range.first + 1)
                continue
            }
            if (matches.range.last >= ret.length) break
            matches = regex.find(ret, matches.range.first + 1)
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
        if (!signature.contains('(')) {
            return null
        }
        val method = signature.substring(owner.length, signature.indexOf('('))
        val descriptor = signature.substring(signature.indexOf('('))
        if (!methodDescriptor.matches(descriptor)) return null
        val ownerDef = mapping.defaultNamespaceClassMap[ownerQ] ?: return null
        val methodMapped = ownerDef.methods.asSequence().filter {
            it.getName(defaultNamespace) == method && it.getDescriptor(defaultNamespace) == descriptor
        }.map { it.getName(to) }.distinct().toList().let {
            if (it.size == 1) it[0] else {
                if (it.size > 1) {
                    LOGGER.error("Found multiple methods with the same name and descriptor: $it")
                    error("Found multiple methods with the same name and descriptor: $it")
                }
                null
            }
        } ?: mapping.classes.asSequence().flatMap { it.methods }.firstOrNull {
            it.getName(defaultNamespace) == method && it.getDescriptor(defaultNamespace) == descriptor
        }?.getName(to) // find mapping in all classes
        return "L${ownerDef.getName(to)};${methodMapped ?: method}${translate(descriptor, to)}"
    }
}
