/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.fontbox.cmap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import junit.framework.TestCase;

import static com.tom_roush.fontbox.cmap.CMap.toInt;

import com.tom_roush.pdfbox.io.RandomAccessReadBufferedFile;

/**
 * This will test the CMapParser implementation.
 *
 */
public class TestCMapParser extends TestCase
{

    /**
     * Check whether the parser and the resulting mapping is working correct.
     *
     * @throws IOException If something went wrong
     */
    public void testLookup() throws IOException
    {
        CMap cMap = new CMapParser().parse(new RandomAccessReadBufferedFile(new File("src/test/resources/fontbox/cmap", "CMapTest")));

        assertEquals("A", cMap.toUnicode(new byte[]{0, 1}));
        assertEquals("0", cMap.toUnicode(new byte[]{1, 0}));
        assertEquals("P", cMap.toUnicode(new byte[]{1, 32}));
        assertEquals("R", cMap.toUnicode(new byte[]{1, 33}));
        assertEquals("*", cMap.toUnicode(new byte[]{0, 10}));
        assertEquals("+", cMap.toUnicode(new byte[]{1, 10}));

        // CID mappings
        assertEquals(65, cMap.toCID(new byte[]{0, 65}));
        assertEquals(0x0118, cMap.toCID(new byte[]{1, 24}));
        assertEquals(0x0208, cMap.toCID(new byte[]{2, 8}));
        assertEquals(0x12C, cMap.toCID(new byte[]{1, 0x2c}));
    }

    public void testIdentity() throws IOException
    {
        CMap cMap = new CMapParser().parsePredefined("Identity-H");

        assertEquals("Indentity-H CID 65", 65, cMap.toCID(65));
        assertEquals("Indentity-H CID 12345", 12345, cMap.toCID(12345));
        assertEquals("Indentity-H CID 0xFFFF", 0xFFFF, cMap.toCID(0xFFFF));
    }

    public void testUniJIS_UCS2_H() throws IOException
    {
        CMap cMap = new CMapParser().parsePredefined("UniJIS-UCS2-H");

        assertEquals("UniJIS-UCS2-H CID 65 -> 34", 34, cMap.toCID(65));
    }

    /**
     * Test the parser against a valid, but poorly formatted CMap file.
     * @throws IOException If something went wrong
     */
    public void testParserWithPoorWhitespace() throws IOException
    {
        CMap cMap = new CMapParser().parse(new RandomAccessReadBufferedFile(new File("src/test/resources/fontbox/cmap", "CMapNoWhitespace")));

        assertNotNull("Failed to parse nasty CMap file", cMap);
    }

    public void testParserWithMalformedbfrange1() throws IOException
    {
        CMap cMap = new CMapParser()
            .parse(new RandomAccessReadBufferedFile(new File("src/test/resources/fontbox/cmap", "CMapMalformedbfrange1")));

        assertNotNull(cMap);
        assertEquals("A", cMap.toUnicode(new byte[]{0, 1}));
        assertNull(cMap.toUnicode(new byte[]{1, 0}));

    }

    public void testParserWithMalformedbfrange2() throws IOException
    {
        CMap cMap = new CMapParser().parse(new RandomAccessReadBufferedFile(new File("src/test/resources/fontbox/cmap", "CMapMalformedbfrange2")));

        assertNotNull(cMap);
        assertEquals("0", cMap.toUnicode(new byte[]{0, 1}));
        assertEquals("A", cMap.toUnicode(new byte[]{2, 0x32}));

        // strict mode
        cMap = new CMapParser(true).parse(new RandomAccessReadBufferedFile(new File("src/test/resources/fontbox/cmap", "CMapMalformedbfrange2")));
        assertNotNull(cMap.toUnicode(new byte[]{2, (byte) 0xF0}));
        assertNull(cMap.toUnicode(new byte[]{2, (byte) 0xF1}));
    }

    public void testPredefinedMap() throws IOException
    {
        CMap cMap = new CMapParser().parsePredefined("Adobe-Korea1-UCS2");
        assertNotNull("Failed to parse predefined CMap Adobe-Korea1-UCS2", cMap);

        assertEquals("wrong CMap name", "Adobe-Korea1-UCS2", cMap.getName());
        assertEquals("wrong WMode", 0, cMap.getWMode());
        assertFalse(cMap.hasCIDMappings());
        assertTrue(cMap.hasUnicodeMappings());

        cMap = new CMapParser().parsePredefined("Identity-V");
        assertNotNull("Failed to parse predefined CMap Identity-V", cMap);
    }

    public void testIdentitybfrange() throws IOException
    {
        // use strict mode
        CMap cMap = new CMapParser(true)
            .parse(new RandomAccessReadBufferedFile(new File("src/test/resources/fontbox/cmap", "Identitybfrange")));
        assertEquals("wrong CMap name", "Adobe-Identity-UCS", cMap.getName());

        Charset UTF_16BE = Charset.forName("UTF-16BE");

        byte[] bytes = new byte[] { 0, 0x48 };
        assertEquals("Indentity 0x0048", new String(bytes, UTF_16BE), cMap.toUnicode(0x0048));

        bytes = new byte[] { 0x30, 0x39 };
        assertEquals("Indentity 0x3039", new String(bytes, UTF_16BE), cMap.toUnicode(0x3039));

        // check border values for strict mode
        bytes = new byte[] { 0x30, (byte) 0xFF };
        assertEquals("Indentity 0x30FF", new String(bytes, UTF_16BE), cMap.toUnicode(0x30FF));
        // check border values for strict mode
        bytes = new byte[] { 0x31, 0x00 };
        assertEquals("Indentity 0x3100", new String(bytes, UTF_16BE), cMap.toUnicode(0x3100));

        bytes = new byte[] { (byte) 0xFF, (byte) 0xFF };
        assertEquals("Indentity 0xFFFF", new String(bytes, UTF_16BE), cMap.toUnicode(0xFFFF));

    }

}
