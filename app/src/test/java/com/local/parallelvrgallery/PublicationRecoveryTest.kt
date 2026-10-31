package com.local.parallelvrgallerypro

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublicationRecoveryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun killAfterAnyOldMoveRestoresEveryOldResultBeforeScanning() {
        for (movesBeforeKill in 0..2) {
            val root = folder.newFolder("case-$movesBeforeKill")
            val cache = File(root,"cache").also { it.mkdir() }
            val staging = File(root,"work").also { it.mkdir() }
            val work = File(staging,"new").also { it.mkdir(); File(it,"left.jpg").writeText("new") }
            val target = File(cache,"target").also { it.mkdir(); File(it,"left.jpg").writeText("old target") }
            val selected = File(cache,"selected").also { it.mkdir(); File(it,"left.jpg").writeText("old selected") }
            val t = ImageCachePublication.prepare(work,target,listOf(selected))
            t.oldToBackup.take(movesBeforeKill).forEach { (old,backup) -> assertTrue(old.renameTo(backup)) }
            assertTrue(ImageCachePublication.recover(staging,cache).isEmpty())
            assertEquals("old target",File(target,"left.jpg").readText())
            assertEquals("old selected",File(selected,"left.jpg").readText())
            assertFalse(t.journal.exists())
        }
    }

    @Test fun killAfterNewRenameBeforeCompletionSidecarKeepsNewResult() {
        val cache=folder.newFolder("cache");val staging=folder.newFolder("work")
        val work=File(staging,"new").also { it.mkdir();File(it,"left.jpg").writeText("new") }
        val target=File(cache,"target").also { it.mkdir();File(it,"left.jpg").writeText("old") }
        val selected=File(cache,"selected").also { it.mkdir();File(it,"left.jpg").writeText("selected") }
        val history=File(cache,"history").also { it.mkdir();File(it,"left.jpg").writeText("history") }
        val t=ImageCachePublication.prepare(work,target,listOf(selected))
        t.oldToBackup.forEach { (old,backup) -> assertTrue(old.renameTo(backup)) }
        assertTrue(work.renameTo(target))
        assertTrue(ImageCachePublication.recover(staging,cache).isEmpty())
        assertEquals("new",File(target,"left.jpg").readText())
        assertFalse(selected.exists())
        assertEquals("history",File(history,"left.jpg").readText())
        assertFalse(t.journal.exists())
    }

    @Test fun corruptJournalCannotMoveOrDeleteOutsideRoots() {
        val cache=folder.newFolder("cache");val staging=folder.newFolder("work")
        val untouched=folder.newFile("outside").also { it.writeText("private") }
        File(staging,"transaction-untrusted.properties").writeText("id=00000000-0000-0000-0000-000000000000\nwork=${staging.path}\ntarget=${untouched.path}\ncount=0")
        assertEquals(1,ImageCachePublication.recover(staging,cache).size)
        assertEquals("private",untouched.readText())
    }
}
