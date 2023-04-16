package com.github.ordom.remapper.mapping.tsrg

import com.github.ordom.remapper.VERSION
import net.fabricmc.mapping.reader.v2.MappingGetter
import net.fabricmc.mapping.reader.v2.TinyMetadata
import net.fabricmc.mapping.reader.v2.TinyVisitor
import net.fabricmc.mapping.tree.*
import java.io.File
import java.io.OutputStreamWriter

class TsrgTree(
    internal var metadata: TinyMetadata
): TinyTree {
    var from: String = ""
    var to: String = ""
    internal val map: MutableMap<String, ClassDef> = mutableMapOf()
    internal val classes = mutableListOf<ClassImpl>()
    override fun getMetadata() = metadata
    override fun getDefaultNamespaceClassMap(): MutableMap<String, ClassDef> = map
    override fun getClasses(): MutableCollection<ClassImpl> = classes
    fun dump(file: File) {
        file.outputStream().writer().use {
            dump(it)
        }
    }

    fun dump(writer: OutputStreamWriter) {
        writer.write("tiny\t2\t0\t${metadata.namespaces.joinToString("\t")}\n")
        val ns = metadata.namespaces
        writer.write(classes.joinToString("\n") { cls ->
            var str = "c\t${ns.joinToString("\t") { cls.getName(it) }}\n"
            cls.fields.forEach { fieldDef ->
                str += "\tf\t${fieldDef.defaultSignature}\t" +
                        "${ns.joinToString("\t") { fieldDef.getName(it) }}\n"
            }
            cls.methods.forEach { methodDef ->
                str += "\tm\t${methodDef.defaultSignature}\t" +
                        "${ns.joinToString("\t") { methodDef.getName(it) }}\n"
            }
            str.dropLast(1)
        })
    }

    open class MappedImpl(
        val names: Array<String>,
        val namespaceMapping: (String) -> Int = TSRG2TINY_METADATA::index
    ): Mapped {
        override fun getName(namespace: String) = names[namespaceMapping(namespace)]
        override fun getRawName(namespace: String) = getName(namespace)
        override fun getComment() = "Generated by OrdomalRemapper v$VERSION"
    }

    class ClassImpl(names: Array<String>, namespaceMapping: (String) -> Int = TSRG2TINY_METADATA::index): MappedImpl(names, namespaceMapping), ClassDef {
        @JvmField
        internal val methods = mutableListOf<MethodImpl>()
        @JvmField
        internal val fields = mutableListOf<FieldImpl>()
        override fun getMethods(): MutableCollection<MethodImpl> = methods
        override fun getFields(): MutableCollection<FieldImpl> = fields
    }

    open class DescriptoredImpl(names: Array<String>, internal val defaultSignature: String?, private val translator: SignatureTranslator, namespaceMapping: (String) -> Int = TSRG2TINY_METADATA::index): MappedImpl(names, namespaceMapping),
        Descriptored {
        override fun getDescriptor(namespace: String): String? = if (namespaceMapping(namespace) == 0 || defaultSignature == null) defaultSignature else translator.translate(defaultSignature, namespace)
    }

    class MethodImpl(names: Array<String>, descriptor: String?, translator: SignatureTranslator, namespaceMapping: (String) -> Int = TSRG2TINY_METADATA::index):
        DescriptoredImpl(names, descriptor, translator, namespaceMapping), MethodDef {
        @JvmField
        internal val parameters = mutableListOf<ParameterDef>()
        @JvmField
        internal val localVariables = mutableListOf<LocalVariableDef>()
        override fun getParameters(): MutableCollection<ParameterDef> = parameters
        override fun getLocalVariables(): MutableCollection<LocalVariableDef> = localVariables
    }

    class FieldImpl(names: Array<String>, descriptor: String?, translator: SignatureTranslator, namespaceMapping: (String) -> Int = TSRG2TINY_METADATA::index):
        DescriptoredImpl(names, descriptor, translator, namespaceMapping),
        FieldDef

    class ParameterImpl(names: Array<String>, descriptor: String?, private val localVariableIndex: Int,
                        translator: SignatureTranslator, namespaceMapping: (String) -> Int = TSRG2TINY_METADATA::index):
        DescriptoredImpl(names, descriptor, translator, namespaceMapping),
        ParameterDef {
        override fun getLocalVariableIndex() = localVariableIndex
    }

    class TsrgTreeVisitor(
        private val tree: TsrgTree
    ): TinyVisitor {
        private val translator = SignatureTranslator(tree)
        override fun start(metadata: TinyMetadata) {
            tree.metadata = metadata
        }
        override fun pushClass(name: MappingGetter) {
            val cls = ClassImpl(arrayOf(name[0], name[1]))
            tree.map[name[0]] = cls
            tree.classes.add(cls)
        }

        override fun pushField(name: MappingGetter, descriptor: String?) {
            val cls = tree.classes.last()
            cls.fields.add(FieldImpl(arrayOf(name[0], name[1]), descriptor, translator))
        }

        override fun pushMethod(name: MappingGetter, descriptor: String?) {
            val cls = tree.classes.last()
            cls.methods.add(MethodImpl(arrayOf(name[0], name[1]), descriptor, translator))
        }
    }
}
