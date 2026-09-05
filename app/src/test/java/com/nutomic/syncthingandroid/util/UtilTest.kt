package com.nutomic.syncthingandroid.util

import com.google.gson.reflect.TypeToken
import com.nutomic.syncthingandroid.model.Folder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import java.lang.reflect.Type
import java.util.ArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UtilTest {

    @Test
    fun shellQuote_wrapsInSingleQuotes() {
        assertEquals("'abc'", Util.shellQuote("abc"))
    }

    @Test
    fun shellQuote_escapesEmbeddedSingleQuotes() {
        // POSIX style: close quote, escaped quote, reopen quote.
        assertEquals("'it'\\''s'", Util.shellQuote("it's"))
    }

    @Test
    fun shellQuote_neutralizesShellMetacharacters() {
        val malicious = "\$(rm -rf /); `whoami` \"quoted\""
        val quoted = Util.shellQuote(malicious)
        // The quoted form must differ from the raw input and contain no unquoted $ or backtick.
        assertNotEquals(malicious, quoted)
        assertEquals("'$malicious'", quoted)
    }

    @Test
    fun deepCopy_returnsEqualButIndependentCopy() {
        val folder = Folder()
        folder.id = "folder-a"
        folder.label = "Alpha"
        folder.path = "/data/folder-a"

        val type: Type = object : TypeToken<Folder>() {}.type
        val copy = Util.deepCopy(folder, type)

        assertEquals(folder.id, copy.id)
        assertEquals(folder.label, copy.label)
        assertEquals(folder.path, copy.path)

        // Mutating the copy must not affect the original.
        copy.label = "Changed"
        assertEquals("Alpha", folder.label)
    }

    @Test
    fun deepCopy_copiesLists() {
        val folders = ArrayList<Folder>()
        val folder = Folder()
        folder.id = "folder-a"
        folders.add(folder)

        val type: Type = object : TypeToken<List<Folder>>() {}.type
        val copy = Util.deepCopy(folders, type)

        assertEquals(1, copy.size)
        assertEquals("folder-a", copy[0].id)

        copy[0].id = "changed"
        assertEquals("folder-a", folders[0].id)
    }

    @Test
    fun parsePsOutput_rootMatchesFullPathOnly() {
        val output = """
            PID ARGS
              111 /data/app/~~ours==/lib/arm64/libsyncthingnative.so serve --no-browser
              222 /data/app/~~theirs==/lib/arm64/libsyncthingnative.so serve --no-browser
        """.trimIndent()
        val pids = parsePsOutput(
            output,
            "/data/app/~~ours==/lib/arm64/libsyncthingnative.so",
            asRoot = true,
        )
        assertEquals(listOf("111"), pids)
    }

    @Test
    fun parsePsOutput_rootSkipsSuWrapperContainingThePath() {
        // The su wrapper's command line embeds the launch script, which contains the same
        // binary path. Killing it instead of only the core reports a spurious 130 crash.
        val output = """
            PID ARGS
              111 /data/app/~~ours==/lib/arm64/libsyncthingnative.so serve --no-browser
              555 su -c export HOME='/data/user/0/pkg/files'
              777 /data/app/~~ours==/lib/arm64/libsyncthingnative.soX
        """.trimIndent()
        val pids = parsePsOutput(
            output,
            "/data/app/~~ours==/lib/arm64/libsyncthingnative.so",
            asRoot = true,
        )
        assertEquals(listOf("111"), pids)
    }

    @Test
    fun parsePsOutput_nonRootReadsPidFromSecondToken() {
        val output = """
            USER      PID   PPID  VSIZE  RSS   WCHAN    NAME        S
            u0_a123   5678  999   1234   567   0        libsyncthingnative.so S
        """.trimIndent()
        val pids = parsePsOutput(output, "libsyncthingnative.so", asRoot = false)
        assertEquals(listOf("5678"), pids)
    }
}
