package com.github.ordom.remapper.mapping

import com.github.ordom.remapper.SEARGE
import com.github.ordom.remapper.mapping.tsrg.SignatureTranslator
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.fail

private val LOGGER = LoggerFactory.getLogger("I2STest")

class IntermediaryToSrgTest {
    private fun containsFabricClass(signature: String) =
        signature.contains("class_") || signature.contains("method_")
    @Test
    fun merge() {
        val i2s = IntermediaryToSrg(Intermediary(Path.of("run"), "1.19.4"), Srg(Path.of("run"), "1.19.4"))
        val tree = i2s.merge()
        for (i in 0..100) {
            tree.classes.randomOrNull()?.methods?.randomOrNull()?.let {
                assertFalse { containsFabricClass(it.getDescriptor(SEARGE)) }
            }
        }
        val translator = SignatureTranslator(tree)
        val methodSig = translator.mapMethodWithOwner("Lnet/minecraft/class_442;method_25426()V", SEARGE)
            ?: fail("No method mapping found")
        assertFalse { containsFabricClass(methodSig) }
    }
}
