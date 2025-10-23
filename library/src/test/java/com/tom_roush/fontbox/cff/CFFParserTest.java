/*
 * Copyright 2017 The Apache Software Foundation.
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
package com.tom_roush.fontbox.cff;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.tom_roush.fontbox.util.BoundingBox;
import com.tom_roush.pdfbox.io.IOUtils;
import com.tom_roush.pdfbox.io.RandomAccessReadBufferedFile;
import com.tom_roush.tools.FileTools;

/**
 *
 * @author Petr Slaby
 */
public class CFFParserTest
{
    private static CFFType1Font testCFFType1Font;

    @BeforeClass
    public static void loadCFFFont() throws IOException
    {
        List<CFFFont> fonts = readFont("target/fonts/SourceSansProBold.otf");
        testCFFType1Font = (CFFType1Font) fonts.get(0);
    }

    @Test
    public void testFontname()
    {
        assertEquals("SourceSansPro-Bold", testCFFType1Font.getName());
    }

    @Test
    public void testFontBBox() throws IOException
    {
        BoundingBox fontBBox = testCFFType1Font.getFontBBox();
        assertNotNull("FontBBox must not be null", fontBBox);
        assertEquals(-231.0f, fontBBox.getLowerLeftX(), 0.6f);
        assertEquals(-384.0f, fontBBox.getLowerLeftY(), 0.6f);
        assertEquals(1223.0f, fontBBox.getUpperRightX(), 0.6f);
        assertEquals(974.0f, fontBBox.getUpperRightY(), 0.6f);
    }

    @Test
    public void testFontMatrix()
    {
        List<Number> fontMatrix = testCFFType1Font.getFontMatrix();
        assertNotNull("FontMatrix must not be null", fontMatrix);
        assertNumberList("FontMatrix values are different than expected" + fontMatrix.toString(),
                new float[] { 0.001f, 0.0f, 0.0f, 0.001f, 0.0f, 0.0f }, fontMatrix);
    }

    @Test
    public void testCharset()
    {
        CFFCharset charset = testCFFType1Font.getCharset();
        assertNotNull("Charset must not be null", charset);
        assertFalse("isCIDFont has to be false", charset.isCIDFont());
        assertEquals("Charset is not an instance of Format1Charset", "Format1Charset", charset.getClass().getSimpleName());
        // check some randomly chosen mappings
        // gid2name
        assertEquals("Unexpected value for gid2name mapping", ".notdef", charset.getNameForGID(0));

        assertEquals("Unexpected value for gid2name mapping", "space", charset.getNameForGID(1));
        assertEquals("Unexpected value for gid2name mapping", "F", charset.getNameForGID(7));
        assertEquals("Unexpected value for gid2name mapping", "jcircumflex", charset.getNameForGID(300));
        assertEquals("Unexpected value for gid2name mapping", "infinity", charset.getNameForGID(700));
        // gid2sid
        assertEquals("Unexpected value for gid2sid mapping", 0, charset.getSIDForGID(0));
        assertEquals("Unexpected value for gid2sid mapping", 1, charset.getSIDForGID(1));
        assertEquals("Unexpected value for gid2sid mapping", 39, charset.getSIDForGID(7));
        assertEquals("Unexpected value for gid2sid mapping", 585, charset.getSIDForGID(300));
        assertEquals("Unexpected value for gid2sid mapping", 872, charset.getSIDForGID(700));

        // name2sid
        assertEquals("Unexpected value for name2sid mapping", 0, charset.getSID(".notdef"));
        assertEquals("Unexpected value for name2sid mapping", 1, charset.getSID("space"));
        assertEquals("Unexpected value for name2sid mapping", 39, charset.getSID("F"));
        assertEquals("Unexpected value for name2sid mapping", 585, charset.getSID("jcircumflex"));
        assertEquals("Unexpected value for name2sid mapping", 872, charset.getSID("infinity"));
    }

    @Test
    public void voidEncoding()
    {
        CFFEncoding encoding = testCFFType1Font.getEncoding();
        assertNotNull("Encoding must not be null", encoding);
        assertTrue("Encoding is not an instance of CFFStandardEncoding", encoding instanceof CFFStandardEncoding);
    }

    @Test
    public void testCharStringBytess()
    {
        List<byte[]> charStringBytes = testCFFType1Font.getCharStringBytes();
        assertFalse(charStringBytes.isEmpty());
        assertEquals(824, testCFFType1Font.getNumCharStrings());
        // check some randomly chosen values
        assertTrue("Other char strings byte values than expected",
                Arrays.equals(new byte[]{-4, 15, 14}, charStringBytes.get(1)));

        assertTrue("Other char strings byte values than expected",
                Arrays.equals(new byte[]{72, 29, -13, 29, -9, -74, -9, 43, 3, 33, 29, 14},
                        charStringBytes.get(16)));

        assertTrue("Other char strings byte values than expected",
                Arrays.equals(new byte[]{-41, 88, 29, -47, -9, 12, 1, -123, 10, 3, 35, 29, -9,
                        -50, -9, 62, -9, 3, 10, 85, -56, 61, 10}, charStringBytes.get(195)));

        assertTrue("Other char strings byte values than expected",
                Arrays.equals(new byte[]{-5, -69, -61, -8, 28, 1, -9, 57, -39, -65, 29, 14},
                        charStringBytes.get(525)));

        assertTrue("Other char strings byte values than expected",
                Arrays.equals(new byte[]{107, -48, 10, -9, 20, -9, 123, 3, -9, -112, -8, -46, 21,
                        -10, 115, 10}, charStringBytes.get(738)));

    }

    @Test
    public void testGlobalSubrIndex()
    {
        List<byte[]> globalSubrIndex = testCFFType1Font.getGlobalSubrIndex();
        assertFalse(globalSubrIndex.isEmpty());
        assertEquals(278, globalSubrIndex.size());
        // check some randomly chosen values
        assertTrue("Other global subr index values than expected",
                Arrays.equals(new byte[]{21, -70, -83, -85, -72, -72, 105, -85, 92, 91, 105, 107,
                        10, -83, -9, 62, 10}, globalSubrIndex.get(12)));

        assertTrue("Other global subr index values than expected",
                Arrays.equals(new byte[]{58, 122, 29, -5, 48, 6, 11}, globalSubrIndex.get(120)));

        assertTrue("Other global subr index values than expected",
                Arrays.equals(new byte[]{68, 80, 29, -45, -9, 16, -8, -92, 119, 11}, globalSubrIndex.get(253)));
    }

    /**
     * PDFBOX-4038: Test whether BlueValues and other delta encoded lists are read correctly. The test file is from
     * FOP-2432.
     *
     * @throws IOException
     */
    @Test
    public void testDeltaLists()
    {
        @SuppressWarnings("unchecked")
        List<Number> blues = (List<Number>) testCFFType1Font.getPrivateDict().get("BlueValues");

        // Expected values found for this font
        assertNumberList("Blue values are different than expected: " + blues.toString(),
                new int[] { -12, 0, 496, 508, 578, 590, 635, 647, 652, 664, 701, 713 }, blues);

        @SuppressWarnings("unchecked")
        List<Number> otherBlues = (List<Number>) testCFFType1Font.getPrivateDict()
                .get("OtherBlues");
        assertNumberList("Other blues are different than expected: " + otherBlues.toString(),
                new int[] { -196, -184 }, otherBlues);

        @SuppressWarnings("unchecked")
        List<Number> familyBlues = (List<Number>) testCFFType1Font.getPrivateDict()
                .get("FamilyBlues");
        assertNumberList("Other blues are different than expected: " + familyBlues.toString(),
                new int[] { -12, 0, 486, 498, 574, 586, 638, 650, 656, 668, 712, 724 },
                familyBlues);

        @SuppressWarnings("unchecked")
        List<Number> familyOtherBlues = (List<Number>) testCFFType1Font.getPrivateDict()
                .get("FamilyOtherBlues");
        assertNumberList("Other blues are different than expected: " + familyOtherBlues.toString(),
                new int[] { -217, -205 }, familyOtherBlues);

        @SuppressWarnings("unchecked")
        List<Number> stemSnapH = (List<Number>) testCFFType1Font.getPrivateDict().get("StemSnapH");
        assertNumberList("StemSnapH values are different than expected: " + stemSnapH.toString(),
                new int[] { 115 }, stemSnapH);

        @SuppressWarnings("unchecked")
        List<Number> stemSnapV = (List<Number>) testCFFType1Font.getPrivateDict().get("StemSnapV");
        assertNumberList("StemSnapV values are different than expected: " + stemSnapV.toString(),
                new int[] { 146, 150 }, stemSnapV);
    }

    /**
     * PDFBOX-5819: ensure thread safety of Type2CharStringParser when parsing the path of a glyph.
     *
     * @throws InterruptedException
     */
    @Test
    public void testMultiThreadParse() throws InterruptedException
    {
        CountDownLatch latch = new CountDownLatch(2);
        PathRunner pathRunner1 = new PathRunner(latch);
        PathRunner pathRunner2 = new PathRunner(latch);

        AtomicBoolean wasCalled = new AtomicBoolean(false);

        Thread.UncaughtExceptionHandler handler = (t, e) -> wasCalled.set(true);

        Thread thread1 = new Thread(pathRunner1);
        thread1.setUncaughtExceptionHandler(handler);
        Thread thread2 = new Thread(pathRunner2);
        thread2.setUncaughtExceptionHandler(handler);

        thread1.start();
        thread2.start();

        latch.await();
        assertFalse(wasCalled.get());
    }

    private class PathRunner implements Runnable
    {
        private final CountDownLatch latch;

        PathRunner(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void run()
        {
            try
            {
                for (int i = 33; i < 126; i++)
                {
                    testCFFType1Font.getPath(Character.toString((char)i));
                }
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
            finally
            {
                latch.countDown();
            }
        }
    }

    private static List<CFFFont> readFont(String filename) throws IOException
    {
        var file = FileTools.getInternetFile("https://issues.apache.org/jira/secure/attachment/12684264/SourceSansProBold.otf", "SourceSansProBold.otf");
        RandomAccessReadBufferedFile randomAccessRead = new RandomAccessReadBufferedFile(file);
        CFFParser parser = new CFFParser();
        return parser.parse(randomAccessRead);
    }

    private void assertNumberList(String message, int[] expected, List<Number> found)
    {
        assertEquals(message, expected.length, found.size());
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals(message, expected[i], found.get(i).intValue());
        }
    }

    private void assertNumberList(String message, float[] expected, List<Number> found)
    {
        assertEquals(message, expected.length, found.size());
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals(message, expected[i], found.get(i).floatValue(), 0.00f);
        }
    }

}
