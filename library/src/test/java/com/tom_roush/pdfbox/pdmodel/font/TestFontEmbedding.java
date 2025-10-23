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

package com.tom_roush.pdfbox.pdmodel.font;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.tom_roush.fontbox.ttf.OS2WindowsMetricsTable;
import com.tom_roush.fontbox.ttf.TTFParser;
import com.tom_roush.fontbox.ttf.TrueTypeFont;
import com.tom_roush.pdfbox.Loader;
import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.BDDMockito.given;

/**
 * Tests font embedding.
 *
 * @author John Hewson
 * @author Tilman Hausherr
 */
public class TestFontEmbedding
{
    private static final File OUT_DIR = new File("target/test-output");


    @BeforeClass
    public static void setUp()
    {
        OUT_DIR.mkdirs();
    }

    /**
     * Embed a TTF as CIDFontType2.
     *
     * @throws IOException
     */
    @Test
    public void testCIDFontType2() throws IOException
    {
        validateCIDFontType2(false);
    }

    /**
     * Embed a TTF as CIDFontType2 with subsetting.
     *
     * @throws IOException
     */
    @Test
    public void testCIDFontType2Subset() throws IOException
    {
        validateCIDFontType2(true);
    }

    @Test
    public void testBengali() throws IOException
    {
        String BANGLA_TEXT_1 = "আমি কোন পথে ক্ষীরের লক্ষ্মী ষন্ড পুতুল রুপো গঙ্গা ঋষি";
        String BANGLA_TEXT_2 = "দ্রুত গাঢ় শেয়াল অলস কুকুর জুড়ে জাম্প ধুর্ত  হঠাৎ ভাঙেনি মৌলিক ঐশি দৈ";
        String BANGLA_TEXT_3 = "ঋষি কল্লোল ব্যাস নির্ভয় ";

        String expectedExtractedtext = BANGLA_TEXT_1 + "\n" + BANGLA_TEXT_2 + "\n" + BANGLA_TEXT_3;
        File pdf = new File(OUT_DIR, "Bengali.pdf");

        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            final File testFile = new File("src/test/resources/fontbox/ttf/Lohit-Bengali.ttf");
            var input = new FileInputStream(testFile);

            PDFont font = PDType0Font.load(document, input);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page))
            {
                contentStream.beginText();
                contentStream.setFont(font, 18);
                contentStream.newLineAtOffset(10, 750);
                contentStream.showText(BANGLA_TEXT_1);
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText(BANGLA_TEXT_2);
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText(BANGLA_TEXT_3);
                contentStream.endText();
            }

            document.save(pdf);
        }
        // Check text extraction
        String extracted = getUnicodeText(pdf);
        //assertEquals(expectedExtractedtext, extracted.replaceAll("\r", "").trim());
    }

    @Test
    public void testDevanagari() throws IOException
    {
        String DEVANAGARI_TEXT_0 = "प्रदेश ग्रामीण व्यवसायिक, लक्ष्मिपति, लक्षित, मक्खि उपलब्धि, प्रसिद्धि";
        String DEVANAGARI_TEXT_1 = "क्षत्रिय ज्ञानी का शृंगार";
        String DEVANAGARI_TEXT_2 = "खुर्रम खर्चें ट्रक उद्गम लक्ष्मिपति ग्रह शृंगार हृदय लाड़ु विट्ठल टट्टू बुद्धू ढर्रा भ़ुर्ता कम्प्युटर";
        String DEVANAGARI_TEXT_3 = "लक्ष्मिपति रविवार को कम्प्यूटर पर कविता साँईं का नाम लेकर पढ़ता है";

        String expectedExtractedtext = DEVANAGARI_TEXT_0 + "\n" + DEVANAGARI_TEXT_1 + "\n" +
                DEVANAGARI_TEXT_2 + "\n" + DEVANAGARI_TEXT_3;
        File pdf = new File(OUT_DIR, "Devanagari.pdf");

        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            final File testFile = new File("src/test/resources/fontbox/ttf/Lohit-Devanagari.ttf");
            var input = new FileInputStream(testFile);

            PDFont font = PDType0Font.load(document, input);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page))
            {
                contentStream.beginText();
                contentStream.setFont(font, 18);
                contentStream.newLineAtOffset(10, 750);
                contentStream.showText(DEVANAGARI_TEXT_0);
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText(DEVANAGARI_TEXT_1);
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText(DEVANAGARI_TEXT_2);
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText(DEVANAGARI_TEXT_3);
                contentStream.endText();
            }

            document.save(pdf);
        }

        // Check text extraction
        String extracted = getUnicodeText(pdf);
        //assertEquals(expectedExtractedtext, extracted.replaceAll("\r", "").trim());
    }

    @Test
    public void testGujarati() throws IOException
    {
        String GUJARATI_TEXT_0 = "દરેક વ્યક્તિને શિક્ષણનો અધિકાર છે";
        String GUJARATI_TEXT_1 = "શિક્ષિત માણસ વિવિધ પ્રકારના કાર્ય પરિલક્ષિત કરી શકે";
        String GUJARATI_TEXT_2 = "ટ્રક ગૃહ પ્રસિદ્ધિ શ્રમિક અગ્નિ ઠક્કર ઉત્પલ કર્યે";
        String GUJARATI_TEXT_3 = "જ્ઞાની બુદ્ધિમાન ક્રમ ગ્રામ કુર્સી ટ્રુ";

        String expectedExtractedtext = GUJARATI_TEXT_0 + "\n" + GUJARATI_TEXT_1 + "\n" + GUJARATI_TEXT_2 + "\n" + GUJARATI_TEXT_3;
        File pdf = new File(OUT_DIR, "Gujarati.pdf");

        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            final File testFile = new File("src/test/resources/fontbox/ttf/Lohit-Gujarati.ttf");
            var input = new FileInputStream(testFile);

            PDFont font = PDType0Font.load(document, input);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page))
            {
                contentStream.beginText();
                contentStream.setFont(font, 25);
                contentStream.newLineAtOffset(10, 750);
                contentStream.showText(GUJARATI_TEXT_0);
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText(GUJARATI_TEXT_1);
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText(GUJARATI_TEXT_2);
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText(GUJARATI_TEXT_3);
                contentStream.endText();
            }

            document.save(pdf);
        }

        // Check text extraction
        String extracted = getUnicodeText(pdf);
        //assertEquals(expectedExtractedtext, extracted.replaceAll("\r", "").trim());
    }

    /**
     * Test corner case of PDFBOX-4302.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testMaxEntries() throws IOException
    {
        File file;
        String text;
        text = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん" +
                "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン" +
                "１２３４５６７８";

        // The test must have MAX_ENTRIES_PER_OPERATOR unique characters
        Set<Character> set = new HashSet<>(ToUnicodeWriter.MAX_ENTRIES_PER_OPERATOR);
        for (int i = 0; i < text.length(); ++i)
        {
            set.add(text.charAt(i));
        }
        assertEquals(ToUnicodeWriter.MAX_ENTRIES_PER_OPERATOR, set.size());

        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage(PDRectangle.A0);
            document.addPage(page);
            File ipafont = new File("target/fonts/ipag00303", "ipag.ttf");
            assumeTrue(ipafont.exists());
            PDType0Font font = PDType0Font.load(document, ipafont);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page))
            {
                contentStream.beginText();
                contentStream.setFont(font, 20);
                contentStream.newLineAtOffset(50, 3000);
                contentStream.showText(text);
                contentStream.endText();
            }
            file = new File(OUT_DIR, "PDFBOX-4302-test.pdf");
            document.save(file);
        }

        // check that the extracted text matches what we wrote
        String extracted = getUnicodeText(file);
        assertEquals(text, extracted.trim());
    }

    private void validateCIDFontType2(boolean useSubset) throws IOException
    {
        String text;
        File file;
        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            InputStream input = PDFont.class.getResourceAsStream("/com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
            PDType0Font font = PDType0Font.load(document, input, useSubset);
            try (PDPageContentStream stream = new PDPageContentStream(document, page))
            {
                stream.beginText();
                stream.setFont(font, 12);
                text = "Unicode русский язык Tiếng Việt";
                stream.newLineAtOffset(50, 600);
                stream.showText(text);
                stream.endText();
            }
            file = new File(OUT_DIR, "CIDFontType2" + (useSubset ? "-useSubset" : "") + ".pdf");
            document.save(file);
        }

        // check that the extracted text matches what we wrote
        String extracted = getUnicodeText(file);
        assertEquals(text, extracted.trim());
    }

    private String getUnicodeText(File file) throws IOException
    {
        try (PDDocument document = Loader.loadPDF(file))
        {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Test that an embedded and subsetted font can be reused.
     *
     * @throws IOException
     */
    @Test
    public void testReuseEmbeddedSubsettedFont() throws IOException
    {
        String text1 = "The quick brown fox";
        String text2 = "xof nworb kciuq ehT";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage();
            document.addPage(page);

            final File testFile = new File("src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf");
            var inputStream = new FileInputStream(testFile);


            PDType0Font font = PDType0Font.load(document, inputStream);
            try (PDPageContentStream stream = new PDPageContentStream(document, page))
            {
                stream.beginText();
                stream.setFont(font, 20);
                stream.newLineAtOffset(50, 600);
                stream.showText(text1);
                stream.endText();
            }
            document.save(baos);
        }
        // Append, while reusing the font subset
        try (PDDocument document = Loader.loadPDF(baos.toByteArray()))
        {
            PDPage page = document.getPage(0);
            PDFont font = page.getResources().getFont(COSName.getPDFName("F1"));
            try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true))
            {
                stream.beginText();
                stream.setFont(font, 20);
                stream.newLineAtOffset(250, 600);
                stream.showText(text2);
                stream.endText();
            }
            baos.reset();
            document.save(baos);
        }
        // Test that both texts are there
        try (PDDocument document = Loader.loadPDF(baos.toByteArray()))
        {
            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = stripper.getText(document);
            assertEquals(text1 + " " + text2, extractedText.trim());
        }
    }

    private class TrueTypeEmbedderTester extends TrueTypeEmbedder
    {

        /**
         * Common functionality for testing the TrueTypeFontEmbedder
         *
         */
        TrueTypeEmbedderTester(PDDocument document, COSDictionary dict, TrueTypeFont ttf, boolean embedSubset)
                throws IOException
        {
            super(document, dict, ttf, embedSubset);
        }

        @Override
        protected void buildSubset(InputStream ttfSubset, String tag, Map<Integer, Integer> gidToCid)
                throws IOException
        {
            // no-op.  Need to define method to extend abstract class, but
            // this method is not currently needed for testing
        }
    }

    /**
     * Test that we validate embedding permissions properly for all legal permissions combinations
     *
     * @throws IOException
     */
    @Test
    public void testIsEmbeddingPermittedMultipleVersions() throws IOException
    {
        // SETUP
        PDDocument doc = new PDDocument();
        COSDictionary cosDictionary = new COSDictionary();
        final File testFile = new File("src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf");
        TrueTypeFont ttf = new TTFParser().parseEmbedded(new FileInputStream(testFile));

        TrueTypeEmbedderTester tester = new TrueTypeEmbedderTester(doc, cosDictionary, ttf, true);
        TrueTypeFont mockTtf = Mockito.mock(TrueTypeFont.class);
        OS2WindowsMetricsTable mockOS2 = Mockito.mock(OS2WindowsMetricsTable.class);
        given(mockTtf.getOS2Windows()).willReturn(mockOS2);
        Boolean embeddingIsPermitted;

        // TEST 1: 0000 -- Installable embedding versions 0-3+
        given(mockTtf.getOS2Windows().getFsType()).willReturn((short) 0x0000);
        embeddingIsPermitted = tester.isEmbeddingPermitted(mockTtf);

        // VERIFY
        assertTrue(embeddingIsPermitted);

        // no test for 0001, since bit 0 is permanently reserved, and its use is deprecated
        // TEST 2: 0010 -- Restricted License embedding versions 0-3+
        given(mockTtf.getOS2Windows().getFsType()).willReturn((short) 0x0002);
        embeddingIsPermitted = tester.isEmbeddingPermitted(mockTtf);

        // VERIFY
        assertFalse(embeddingIsPermitted);

        // no test for 0011
        // TEST 3: 0100 -- Preview & Print embedding versions 0-3+
        given(mockTtf.getOS2Windows().getFsType()).willReturn((short) 0x0004);
        embeddingIsPermitted = tester.isEmbeddingPermitted(mockTtf);

        // VERIFY
        assertTrue(embeddingIsPermitted);

        // no test for 0101
        // TEST 4: 0110 -- Restricted License embedding AND Preview & Print embedding versions 0-2
        //              -- illegal permissions combination for versions 3+
        given(mockTtf.getOS2Windows().getFsType()).willReturn((short) 0x0006);
        embeddingIsPermitted = tester.isEmbeddingPermitted(mockTtf);

        // VERIFY
        assertTrue(embeddingIsPermitted);

        // no test for 0111
        // TEST 5: 1000 -- Editable embedding versions 0-3+
        given(mockTtf.getOS2Windows().getFsType()).willReturn((short) 0x0008);
        embeddingIsPermitted = tester.isEmbeddingPermitted(mockTtf);

        // VERIFY
        assertTrue(embeddingIsPermitted);

        // no test for 1001
        // TEST 6: 1010 -- Restricted License embedding AND Editable embedding versions 0-2
        //              -- illegal permissions combination for versions 3+
        given(mockTtf.getOS2Windows().getFsType()).willReturn((short) 0x000A);
        embeddingIsPermitted = tester.isEmbeddingPermitted(mockTtf);

        // VERIFY
        assertTrue(embeddingIsPermitted);

        // no test for 1011
        // TEST 7: 1100 -- Editable embedding AND Preview & Print embedding versions 0-2
        //              -- illegal permissions combination for versions 3+
        given(mockTtf.getOS2Windows().getFsType()).willReturn((short) 0x000C);
        embeddingIsPermitted = tester.isEmbeddingPermitted(mockTtf);

        // VERIFY
        assertTrue(embeddingIsPermitted);

        // no test for 1101
        // TEST 8: 1110 Editable embedding AND Preview & Print embedding AND Restricted License embedding versions 0-2
        //              -- illegal permissions combination for versions 3+
        given(mockTtf.getOS2Windows().getFsType()).willReturn((short) 0x000E);
        embeddingIsPermitted = tester.isEmbeddingPermitted(mockTtf);

        // VERIFY
        assertTrue(embeddingIsPermitted);

        // no test for 1111
    }

    @Test
    public void testSurrogatePairCharacterExceptionIsBmpCodePoint() throws IOException
    {
        final String message = "あ";

        try (PDDocument doc = new PDDocument())
        {
            PDPage page = new PDPage();
            doc.addPage(page);
            final File testFile = new File("src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf");

            PDFont font = PDType0Font.load(doc, testFile);

            try (PDPageContentStream contents = new PDPageContentStream(doc, page))
            {
                contents.beginText();
                contents.setFont(font, 64);
                contents.newLineAtOffset(100, 700);
                contents.showText(message);
                contents.endText();
            }
            catch (IllegalStateException | IllegalArgumentException e)
            {
                assertEquals("No glyph for U+3042 (あ) in font LiberationSans", e.getMessage());
                return;
            }

            fail();
        }
    }

    @Test
    public void testSurrogatePairCharacterExceptionIsValidCodePoint() throws IOException
    {
        final String message = "𩸽";
        try (PDDocument doc = new PDDocument())
        {
            PDPage page = new PDPage();
            doc.addPage(page);

            final File testFile = new File("src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf");
            var inputStream = new FileInputStream(testFile);

            PDFont font = PDType0Font.load(doc,inputStream);

            try (PDPageContentStream contents = new PDPageContentStream(doc, page))
            {
                contents.beginText();
                contents.setFont(font, 64);
                contents.newLineAtOffset(100, 700);
                contents.showText(message);
                contents.endText();
            }
            catch (IllegalStateException | IllegalArgumentException e)
            {
                assertEquals("No glyph for U+29E3D (鸽) in font LiberationSans", e.getMessage());
                return;
            }

            fail();
        }
    }

    /**
     * PDFBOX-5230: Zero-width characters should be invisible.
     *
     * @throws IOException
     */
    @Test
    public void testEmbeddedFontWithZeroWidthChars() throws IOException
    {
        String text = "AAA\u200CBBB";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage();
            document.addPage(page);

            final File testFile = new File("src/test/resources/fontbox/ttf/LiberationSans-Regular.ttf");
            var input = new FileInputStream(testFile);

            PDType0Font font = PDType0Font.load(document, input);
            try (PDPageContentStream stream = new PDPageContentStream(document, page))
            {
                stream.beginText();
                stream.setFont(font, 20);
                stream.newLineAtOffset(50, 600);
                stream.showText(text);
                stream.endText();
            }
            document.save(baos);
        }
        try (PDDocument document = Loader.loadPDF(baos.toByteArray()))
        {
            // verify that the text still contains zero-width characters
            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = stripper.getText(document).trim();
            assertEquals(text, extractedText);
            assertEquals(7, extractedText.length());
            assertEquals('\u200C', extractedText.charAt(3));

            // verify that the zero-width characters are invisible
            PDPage page = document.getPage(0);
            PDResources resources = page.getResources();
            Iterable< COSName > fontNames = resources.getFontNames();
            COSName fontName = fontNames.iterator().next();
            PDType0Font font = (PDType0Font) resources.getFont(fontName);
            byte[] encoded = font.encode('\u200C');
            int code = ((encoded[0] & 0xFF) << 8) | (encoded[1] & 0xFF);
            assertEquals(0f, font.getWidth(code), 0.00f);
            assertEquals(0f, font.getWidthFromFont(code), 0.00f);

            assertFalse(font.isDamaged());
        }
    }
}
