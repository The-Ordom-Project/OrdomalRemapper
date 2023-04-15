package com.github.ordom.remapper.mapping

import com.github.ordom.remapper.CLASS_MAPPED_SEARGE
import com.github.ordom.remapper.INTERMEDIARY
import com.github.ordom.remapper.SEARGE
import com.github.ordom.remapper.mapping.tsrg.SignatureTranslator
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

private val LOGGER = LoggerFactory.getLogger("I2STest")

class IntermediaryToSrgTest {
    private fun containsFabricClass(signature: String) =
        signature.contains("class_") || signature.contains("method_")

    @Test
    fun merge() {
        val i2s = IntermediaryToSrg(Path.of("run"), "1.19.4")
        val tree = i2s.merge()
        val translator = SignatureTranslator(tree)
        assertEquals(
            "Lnet/minecraft/client/gui/screens/Screen;m_7856_()V",
            translator.mapMethodWithOwner("Lnet/minecraft/class_437;method_25426()V", CLASS_MAPPED_SEARGE),
        )
        for (i in 0..1000) {
            val method = tree.classes.randomOrNull()?.methods?.randomOrNull()
            method?.let {
                val sig = it.getDescriptor(CLASS_MAPPED_SEARGE)
                if (containsFabricClass(sig)) {
                    fail("Found fabric class in remapped method signature: $sig, " +
                            "Method: ${method.getName(INTERMEDIARY)}${method.getDescriptor(INTERMEDIARY)}")
                }
            }
        }
    }
}
