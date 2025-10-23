/*
 * Copyright 2015 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.fontbox.ttf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import com.tom_roush.fontbox.util.autodetect.FontFileFinder;
import com.tom_roush.pdfbox.io.RandomAccessReadBuffer;
import com.tom_roush.pdfbox.io.RandomAccessReadBufferedFile;
import com.tom_roush.tools.FileTools;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Path;
import android.graphics.RectF;

/**
 *
 * @author Tilman Hausherr
 */
@RunWith(RobolectricTestRunner.class)
public class TTFSubsetterTest
{

    /**
     * Test of PDFBOX-2854: empty subset with all tables.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testEmptySubset() throws IOException
    {
        TrueTypeFont x = new TTFParser().parse(new RandomAccessReadBufferedFile(
                "src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf"));
        TTFSubsetter ttfSubsetter = new TTFSubsetter(x);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ttfSubsetter.writeToStream(baos);
        try (TrueTypeFont subset = new TTFParser(true)
                .parse(new RandomAccessReadBuffer(baos.toByteArray())))
        {
            assertEquals(1, subset.getNumberOfGlyphs());
            assertEquals(0, subset.nameToGID(".notdef"));
            assertNotNull(subset.getGlyph().getGlyph(0));
        }
    }

    /**
     * Test of PDFBOX-2854: empty subset with selected tables.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testEmptySubset2() throws IOException
    {
        TrueTypeFont x = new TTFParser().parse(new RandomAccessReadBufferedFile("src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf"));
        // List copied from TrueTypeEmbedder.java
        List<String> tables = new ArrayList<>();
        tables.add("head");
        tables.add("hhea");
        tables.add("loca");
        tables.add("maxp");
        tables.add("cvt ");
        tables.add("prep");
        tables.add("glyf");
        tables.add("hmtx");
        tables.add("fpgm");
        tables.add("gasp");
        TTFSubsetter ttfSubsetter = new TTFSubsetter(x, tables);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ttfSubsetter.writeToStream(baos);
        try (TrueTypeFont subset = new TTFParser(true)
                .parse(new RandomAccessReadBuffer(baos.toByteArray())))
        {
            assertEquals(1, subset.getNumberOfGlyphs());
            assertEquals(0, subset.nameToGID(".notdef"));
            assertNotNull(subset.getGlyph().getGlyph(0));
        }
    }

    /**
     * Test of PDFBOX-2854: subset with one glyph.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testNonEmptySubset() throws IOException
    {
        TrueTypeFont full = new TTFParser().parse(new RandomAccessReadBufferedFile(
                "src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf"));
        TTFSubsetter ttfSubsetter = new TTFSubsetter(full);
        ttfSubsetter.add('a');
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ttfSubsetter.writeToStream(baos);
        try (TrueTypeFont subset = new TTFParser(true)
                .parse(new RandomAccessReadBuffer(baos.toByteArray())))
        {
            assertEquals(2, subset.getNumberOfGlyphs());
            assertEquals(0, subset.nameToGID(".notdef"));
            assertEquals(1, subset.nameToGID("a"));
            assertNotNull(subset.getGlyph().getGlyph(0));
            assertNotNull(subset.getGlyph().getGlyph(1));
            assertNull(subset.getGlyph().getGlyph(2));
            assertEquals(full.getAdvanceWidth(full.nameToGID("a")),
                    subset.getAdvanceWidth(subset.nameToGID("a")));
            assertEquals(full.getHorizontalMetrics().getLeftSideBearing(full.nameToGID("a")),
                    subset.getHorizontalMetrics().getLeftSideBearing(subset.nameToGID("a")));
        }
    }

    /**
     * Test of PDFBOX-3319: check that widths and left side bearings in partially monospaced font
     * are kept.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testPDFBox3319() throws IOException
    {
        System.out.println("Searching for SimHei font...");
        FontFileFinder fontFileFinder = new FontFileFinder();
        List<URI> files = fontFileFinder.find();
        File simhei = new File("src/test/resources/fontbox/ttf/SimHei.ttf");
        assertTrue("SimHei font not available on this machine, test skipped", simhei != null);
        System.out.println("SimHei font found!");
        TrueTypeFont full = new TTFParser().parse(new RandomAccessReadBufferedFile(simhei));

        // List copied from TrueTypeEmbedder.java
        // Without it, the test would fail because of missing post table in source font
        List<String> tables = new ArrayList<>();
        tables.add("head");
        tables.add("hhea");
        tables.add("loca");
        tables.add("maxp");
        tables.add("cvt ");
        tables.add("prep");
        tables.add("glyf");
        tables.add("hmtx");
        tables.add("fpgm");
        tables.add("gasp");

        TTFSubsetter ttfSubsetter = new TTFSubsetter(full, tables);

        String chinese = "中国你好!";
        for (int offset = 0; offset < chinese.length();)
        {
            int codePoint = chinese.codePointAt(offset);
            ttfSubsetter.add(codePoint);
            offset += Character.charCount(codePoint);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ttfSubsetter.writeToStream(baos);
        try (TrueTypeFont subset = new TTFParser(true)
                .parse(new RandomAccessReadBuffer(baos.toByteArray())))
        {
            assertEquals(6, subset.getNumberOfGlyphs());

            for (Entry<Integer, Integer> entry : ttfSubsetter.getGIDMap().entrySet())
            {
                Integer newGID = entry.getKey();
                Integer oldGID = entry.getValue();
                assertEquals(full.getAdvanceWidth(oldGID), subset.getAdvanceWidth(newGID));
                assertEquals(full.getHorizontalMetrics().getLeftSideBearing(oldGID),
                        subset.getHorizontalMetrics().getLeftSideBearing(newGID));
            }
        }
    }

    /**
     * Test of PDFBOX-3379: check that left side bearings in partially monospaced font are kept.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testPDFBox3379() throws IOException
    {
        var file = FileTools.getInternetFile("https://issues.apache.org/jira/secure/attachment/12809395/DejaVuSansMono.ttf", "target/pdfs/DejaVuSansMono.ttf");
        TrueTypeFont full = new TTFParser()
                .parse(new RandomAccessReadBufferedFile(file));
        TTFSubsetter ttfSubsetter = new TTFSubsetter(full);
        ttfSubsetter.add('A');
        ttfSubsetter.add(' ');
        ttfSubsetter.add('B');
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ttfSubsetter.writeToStream(baos);
        try (TrueTypeFont subset = new TTFParser()
                .parse(new RandomAccessReadBuffer(baos.toByteArray())))
        {
            assertEquals(4, subset.getNumberOfGlyphs());
            assertEquals(0, subset.nameToGID(".notdef"));
            assertEquals(1, subset.nameToGID("space"));
            assertEquals(2, subset.nameToGID("A"));
            assertEquals(3, subset.nameToGID("B"));
            String [] names = {"A","B","space"};
            for (String name : names)
            {
                assertEquals(full.getAdvanceWidth(full.nameToGID(name)),
                        subset.getAdvanceWidth(subset.nameToGID(name)));
                assertEquals(full.getHorizontalMetrics().getLeftSideBearing(full.nameToGID(name)),
                        subset.getHorizontalMetrics().getLeftSideBearing(subset.nameToGID(name)));
            }
        }
    }

    /**
     * Test of PDFBOX-3757: check that PostScript names that are not part of WGL4Names don't get
     * shuffled in buildPostTable().
     *
     * @throws java.io.IOException
     */
    @Test
    public void testPDFBox3757() throws IOException
    {
        final File testFile = new File("src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf");
        TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBufferedFile(testFile));
        TTFSubsetter ttfSubsetter = new TTFSubsetter(ttf);
        ttfSubsetter.add('Ö');
        ttfSubsetter.add('\u200A');
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ttfSubsetter.writeToStream(baos);
        try (TrueTypeFont subset = new TTFParser(true)
                .parse(new RandomAccessReadBuffer(baos.toByteArray())))
        {
            assertEquals(5, subset.getNumberOfGlyphs());

            assertEquals(0, subset.nameToGID(".notdef"));
            assertEquals(1, subset.nameToGID("O"));
            assertEquals(2, subset.nameToGID("Odieresis"));
            assertEquals(3, subset.nameToGID("uni200A"));
            assertEquals(4, subset.nameToGID("dieresis.uc"));

            PostScriptTable pst = subset.getPostScript();
            assertEquals(".notdef", pst.getName(0));
            assertEquals("O", pst.getName(1));
            assertEquals("Odieresis", pst.getName(2));
            assertEquals("uni200A", pst.getName(3));
            assertEquals("dieresis.uc", pst.getName(4));

            assertTrue("Hair space path should be empty", isTrulyEmpty(subset.getPath("uni200A")));

            assertFalse("UC dieresis path should not be empty", subset.getPath("dieresis.uc").isEmpty());
        }
    }

    /**
     * Test font with v3 PostScript table format and no glyph names.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox5728() throws IOException
    {
        var font = FileTools.getInternetFile("https://issues.apache.org/jira/secure/attachment/13065025/NotoMono-Regular.ttf", "target/fonts/NotoMono-Regular.ttf");
        try (TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBufferedFile(font)))
        {
            PostScriptTable postScript = ttf.getPostScript();
            assertEquals(3.0, postScript.getFormatType(), 0.004f);
            assertNull(postScript.getGlyphNames());
            TTFSubsetter subsetter = new TTFSubsetter(ttf);
            subsetter.add('a');
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            subsetter.writeToStream(output);
        }
    }

    /**
     * Test of PDFBOX-5230: check that subsetting can be forced to use invisible glyphs.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testPDFBox5230() throws IOException
    {
        final File testFile = new File("src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf");
        TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBufferedFile(testFile));
        TTFSubsetter ttfSubsetter = new TTFSubsetter(ttf);
        ttfSubsetter.add('A');
        ttfSubsetter.add('B');
        ttfSubsetter.add('\u200C');

        // verify results without forcing

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ttfSubsetter.writeToStream(baos);
        try (TrueTypeFont subset = new TTFParser(true)
                .parse(new RandomAccessReadBuffer(baos.toByteArray())))
        {
            assertEquals(4, subset.getNumberOfGlyphs());
            assertEquals(0, subset.nameToGID(".notdef"));
            assertEquals(1, subset.nameToGID("A"));
            assertEquals(2, subset.nameToGID("B"));
            assertEquals(3, subset.nameToGID("uni200C"));

            PostScriptTable pst = subset.getPostScript();
            assertEquals(".notdef", pst.getName(0));
            assertEquals("A", pst.getName(1));
            assertEquals("B", pst.getName(2));
            assertEquals("uni200C", pst.getName(3));

            assertFalse("A path should not be empty", subset.getPath("A").isEmpty());
            assertFalse("B path should not be empty", subset.getPath("B").isEmpty());
            assertFalse("ZWNJ path should not be empty", subset.getPath("uni200C").isEmpty());

            assertNotEquals("A width should not be zero.", 0, subset.getWidth("A"));
            assertNotEquals("B width should not be zero.", 0, subset.getWidth("B"));
            assertEquals("ZWNJ width should be zero", 0f, subset.getWidth("uni200C"), 0.05f);
        }

        // verify results while forcing B and ZWNJ to use invisible glyphs

        ttfSubsetter.forceInvisible('B');
        ttfSubsetter.forceInvisible('\u200C');
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        ttfSubsetter.writeToStream(baos2);
        try (TrueTypeFont subset = new TTFParser(true)
                .parse(new RandomAccessReadBuffer(baos2.toByteArray())))
        {
            assertEquals(4, subset.getNumberOfGlyphs());
            assertEquals(0, subset.nameToGID(".notdef"));
            assertEquals(1, subset.nameToGID("A"));
            assertEquals(2, subset.nameToGID("B"));
            assertEquals(3, subset.nameToGID("uni200C"));

            PostScriptTable pst = subset.getPostScript();
            assertEquals(".notdef", pst.getName(0));
            assertEquals("A", pst.getName(1));
            assertEquals("B", pst.getName(2));
            assertEquals("uni200C", pst.getName(3));

            assertFalse("A path should not be empty", isTrulyEmpty(subset.getPath("A")));
            assertTrue("B path should be empty", isTrulyEmpty(subset.getPath("B")));
            assertTrue("ZWNJ path should be empty", subset.getPath("uni200C").isEmpty());

            assertNotEquals("A width should not be zero.", 0, subset.getWidth("A"));
            assertEquals("B width should be zero.", 0, subset.getWidth("B"), 0.005f);
            assertEquals("ZWNJ width should be zero", 0, subset.getWidth("uni200C"), 0.005f);
        }
    }

    public static boolean isTrulyEmpty(Path path) {
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        return bounds.isEmpty();
    }

    /**
     * PDFBOX-6015: test font with 0/1 cmap.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox6015() throws IOException
    {
        var font = FileTools.getInternetFile("https://issues.apache.org/jira/secure/attachment/13076859/Keyboard.ttf", "target/fonts/Keyboard.ttf");
        try (TrueTypeFont ttf = new TTFParser()
                .parse(new RandomAccessReadBufferedFile(font)))
        {
            CmapLookup unicodeCmapLookup = ttf.getUnicodeCmapLookup();
            assertEquals(185, unicodeCmapLookup.getGlyphId('a'));
            assertEquals(210, unicodeCmapLookup.getGlyphId('z'));
            assertEquals(159, unicodeCmapLookup.getGlyphId('A'));
            assertEquals(184, unicodeCmapLookup.getGlyphId('Z'));
            assertEquals(49, unicodeCmapLookup.getGlyphId('0'));
            assertEquals(58, unicodeCmapLookup.getGlyphId('9'));
        }
    }
}
