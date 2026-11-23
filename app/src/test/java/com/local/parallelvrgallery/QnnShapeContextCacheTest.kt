package com.local.parallelvrgallerypro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class QnnShapeContextCacheTest {
    @Rule @JvmField val temporary = TemporaryFolder()

    @Test fun diskContextPolicyAndActualShapeKeys() {
        val directory = temporary.newFolder()
        val writes = QnnShapeContextCache.WriteGate()
        val cache = QnnShapeContextCache(directory, "device-a/runtime-a", writes)
        val changedRuntime = QnnShapeContextCache(directory, "device-a/runtime-b", writes)
        val landscape = cache.pathFor("model-a", 1386, 770)

        assertTrue(QnnShapeContextCache.supports(QnnDepthAspect.ALIGNED))
        assertTrue(QnnShapeContextCache.supports(QnnDepthAspect.WIDE_TALL))
        assertTrue(QnnShapeContextCache.supports(QnnDepthAspect.PORTRAIT_PAIR))
        assertFalse(QnnShapeContextCache.supports(QnnDepthAspect.SQUARE))
        assertFalse(QnnShapeContextCache.supports(QnnDepthAspect.FIT))
        assertEquals(directory, landscape.parentFile)
        assertNotEquals(landscape, cache.pathFor("model-a", 770, 1386))
        assertNotEquals(landscape, cache.pathFor("model-b", 1386, 770))
        assertNotEquals(landscape, changedRuntime.pathFor("model-a", 1386, 770))
    }

    @Test fun embeddedGraphAppearsOnlyAfterCompilationAndCanBeReopened() {
        val directory = temporary.newFolder()
        val cache = QnnShapeContextCache(directory, "compatible", QnnShapeContextCache.WriteGate())
        val target = cache.pathFor("model", 518, 518)

        val compiled = cache.open("model", 518, 518, { error("no graph yet") }) { candidate ->
            assertFalse(target.exists())
            assertFalse(requireNotNull(candidate).exists())
            candidate.writeText("embedded-graph")
            assertFalse(target.exists())
            "compiled"
        }
        assertEquals(QnnShapeContextCache.Source.GENERATED, compiled.source)
        assertEquals(listOf(target), directory.listFiles()!!.toList())

        val loaded = cache.open("model", 518, 518, { it.readText() }, { error("unexpected compile") })
        assertEquals(QnnShapeContextCache.Source.DISK, loaded.source)
        assertEquals("embedded-graph", loaded.value)
    }

    @Test fun rejectedCachedGraphIsReplacedWithoutRemovingItsNeighbor() {
        val cache = QnnShapeContextCache(temporary.newFolder(), "compatible", QnnShapeContextCache.WriteGate())
        val target = cache.pathFor("model", 518, 518)
        target.writeText("corrupt")
        val other = cache.pathFor("model", 700, 378).also { it.writeText("valid") }

        val result = cache.open("model", 518, 518, { throw IOException("bad cached graph") }) { candidate ->
            assertFalse(target.exists())
            requireNotNull(candidate).writeText("replacement")
            "fresh"
        }
        assertEquals(QnnShapeContextCache.Source.GENERATED, result.source)
        assertEquals("replacement", target.readText())
        assertEquals("valid", other.readText())
    }

    @Test fun unsupportedGenerationFallsBackAndSuppressesLaterGenerationForThisProcess() {
        val directory = temporary.newFolder()
        val writeGate = QnnShapeContextCache.WriteGate()
        val cache = QnnShapeContextCache(directory, "compatible", writeGate)
        var attempts = 0
        val initial = cache.open("model", 518, 518, { error("unexpected load") }) { candidate ->
            attempts++
            if (candidate != null) {
                candidate.writeText("partial")
                throw IOException("device rejects serialization")
            }
            "fresh"
        }
        assertEquals(QnnShapeContextCache.Source.FRESH, initial.source)
        assertEquals(2, attempts)
        assertTrue(directory.listFiles().isNullOrEmpty())

        val otherCache = QnnShapeContextCache(directory, "compatible", writeGate)
        otherCache.open("model", 700, 378, { error("unexpected load") }) { assertNull(it) }
    }

    @Test fun cacheTrimsOldestEntriesByBothCountAndTotalBytes() {
        val directory = temporary.newFolder()
        val cache = QnnShapeContextCache(directory, "compatible", QnnShapeContextCache.WriteGate(),
            maxFiles = 2, maxBytes = 7, maxFileBytes = 7)
        fun generate(width: Int, bytes: Int) = cache.open("model", width, 378, { error("unexpected load") }) {
            requireNotNull(it).writeBytes(ByteArray(bytes))
        }

        generate(518, 2)
        val oldest = cache.pathFor("model", 518, 378)
        oldest.setLastModified(1L)
        generate(532, 2)
        generate(546, 2)
        assertFalse(oldest.exists())
        assertTrue(cache.pathFor("model", 532, 378).exists())

        generate(560, 7)
        assertEquals(listOf(cache.pathFor("model", 560, 378)), directory.listFiles()!!.toList())
    }
}
