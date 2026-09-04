package com.nutomic.syncthingandroid.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.nutomic.syncthingandroid.model.Folder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.reflect.TypeToken;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UtilTest {

    @Test
    public void shellQuote_wrapsInSingleQuotes() {
        assertEquals("'abc'", Util.shellQuote("abc"));
    }

    @Test
    public void shellQuote_escapesEmbeddedSingleQuotes() {
        // POSIX style: close quote, escaped quote, reopen quote.
        assertEquals("'it'\\''s'", Util.shellQuote("it's"));
    }

    @Test
    public void shellQuote_neutralizesShellMetacharacters() {
        String malicious = "$(rm -rf /); `whoami` \"quoted\"";
        String quoted = Util.shellQuote(malicious);
        // The quoted form must differ from the raw input and contain no unquoted $ or backtick.
        assertNotEquals(malicious, quoted);
        assertEquals("'" + malicious + "'", quoted);
    }

    @Test
    public void deepCopy_returnsEqualButIndependentCopy() {
        Folder folder = new Folder();
        folder.id = "folder-a";
        folder.label = "Alpha";
        folder.path = "/data/folder-a";

        Type type = new TypeToken<Folder>(){}.getType();
        Folder copy = Util.deepCopy(folder, type);

        assertEquals(folder.id, copy.id);
        assertEquals(folder.label, copy.label);
        assertEquals(folder.path, copy.path);

        // Mutating the copy must not affect the original.
        copy.label = "Changed";
        assertEquals("Alpha", folder.label);
    }

    @Test
    public void deepCopy_copiesLists() {
        List<Folder> folders = new ArrayList<>();
        Folder folder = new Folder();
        folder.id = "folder-a";
        folders.add(folder);

        Type type = new TypeToken<List<Folder>>(){}.getType();
        List<Folder> copy = Util.deepCopy(folders, type);

        assertEquals(1, copy.size());
        assertEquals("folder-a", copy.get(0).id);

        copy.get(0).id = "changed";
        assertEquals("folder-a", folders.get(0).id);
    }
}
