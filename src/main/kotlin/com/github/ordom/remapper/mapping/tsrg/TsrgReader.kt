package com.github.ordom.remapper.mapping.tsrg

import com.github.ordom.remapper.VERSION
import net.fabricmc.mapping.reader.v2.TinyMetadata
import java.io.BufferedReader


internal val TSRG2TINY_METADATA: TinyMetadata = object : TinyMetadata {
    override fun getMajorVersion() = 2
    override fun getMinorVersion() = 0
    override fun getNamespaces() = listOf("official", "srg")
    override fun getProperties(): MutableMap<String, String?> = mutableMapOf(
        "srg_version" to "tsrg2",
        "ordomal_version" to VERSION
    )
}

class TsrgReader(
    private val tsrg: BufferedReader
) {
    fun generateTiny(): TsrgTree {
        val tree = TsrgTree()
        // init
        run {
            val version = tsrg.untilBlank()
            if (version != "tsrg2") {
                throw IllegalArgumentException("Invalid TSRG version: $version, only supports tsrg2")
            }
            tree.from = tsrg.untilBlank()
            tree.to = tsrg.untilBlank()
            if (tsrg.untilBlank() != "id") {
                throw IllegalArgumentException("Invalid TSRG header, expected 'id'")
            }
        }
        // each line
        var lastIndent = 0
        var cls: TsrgTree.ClassImpl? = null
        var mtd: TsrgTree.MethodImpl? = null
        val translator = SignatureTranslator(tree)
        tsrg.lineSequence().forEachIndexed { index, line ->
            val indent = line.indent()
            if (indent > 0) {
                val parts = line.splitBlank()
                when {
                    parts.size == 1 && parts[0] == "static" -> return@forEachIndexed
                    // TSRG dose not contains a signature for fields, just use null.
                    parts.size == 3 -> cls!!.fields.add(TsrgTree.FieldImpl(parts[0], parts[1], null, translator))
                    parts.size == 4 && parts[1].startsWith('(') -> {
                        mtd = TsrgTree.MethodImpl(parts[0], parts[2], parts[1], translator)
                        cls!!.methods.add(mtd!!)
                    }
                    parts.size == 4 && (parts[2].startsWith('p') /* parameter */
                            || parts[2].startsWith('f') /* record field */) -> {
                        val index = parts[0].toInt()
                        mtd!!.parameters.add(TsrgTree.ParameterImpl("arg$index", parts[2], null, index, translator))
                    }
                    else -> throw IllegalArgumentException("Invalid TSRG line: $line (@line $index)")
                }
            }
            else {
                val parts = line.splitBlank()
                cls = TsrgTree.ClassImpl(parts[0], parts[1])
                tree.classes.add(cls!!)
                tree.map[parts[0]] = cls!!
            }
        }
        return tree
    }

    private fun String.indent(): Int {
        var i = 0
        while (i < length) {
            if (this[i] == '\t') i += 4
            else if (this[i] == ' ') i++
            else break
        }
        return i
    }
    private fun String.splitBlank(): List<String> {
        val list = mutableListOf<String>()
        var i = 0
        while (i < length) {
            val ch = this[i]
            if (ch == ' ' || ch == '\t') {
                i++
                continue
            }
            val start = i
            while (i < length) {
                @Suppress("NAME_SHADOWING")
                val ch = this[i]
                if (ch == ' ' || ch == '\t') break
                i++
            }
            list.add(substring(start, i))
        }
        return list
    }
    private fun isBlank(ch: Int): Boolean {
        return ch == ' '.code || ch == '\n'.code || ch == '\t'.code
    }
    private fun BufferedReader.untilBlank(): String {
        val sb = StringBuilder()
        var ch = read()
        while (isBlank(ch) && ch != -1) {
            ch = read() // skip blanks
        }
        while (!isBlank(ch) && ch != -1) {
            sb.append(ch.toChar())
            ch = read()
        }
        return sb.toString()
    }
}