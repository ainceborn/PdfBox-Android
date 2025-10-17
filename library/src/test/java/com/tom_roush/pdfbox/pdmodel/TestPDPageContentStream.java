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
package com.tom_roush.pdfbox.pdmodel;

import java.io.IOException;
import java.util.List;

import com.tom_roush.pdfbox.contentstream.operator.Operator;
import com.tom_roush.pdfbox.contentstream.operator.OperatorName;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSNumber;
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDInlineImage;
import com.tom_roush.pdfbox.pdmodel.graphics.shading.PDShadingType1;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import com.tom_roush.pdfbox.util.Matrix;

import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Yegor Kozlov
 */
public class TestPDPageContentStream extends TestCase
{
    @Test
    public void testSetCmykColors() throws IOException
    {
        try (PDDocument doc = new PDDocument())
        {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page, AppendMode.OVERWRITE, true))
            {
                // pass a non-stroking color in CMYK color space
                contentStream.setNonStrokingColor(0.1f, 0.2f, 0.3f, 0.4f);

                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setNonStrokingColor(1.1f, 0, 0, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setNonStrokingColor(0, 1.1f, 0, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setNonStrokingColor(0, 0, 1.1f, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setNonStrokingColor(0, 0, 0, 1.1f));
            }

            // now read the PDF stream and verify that the CMYK values are correct
            PDFStreamParser parser = new PDFStreamParser(page);
            List<Object> pageTokens = parser.parse();
            // expected five tokens :
            // [0] = COSFloat{0.1}
            // [1] = COSFloat{0.2}
            // [2] = COSFloat{0.3}
            // [3] = COSFloat{0.4}
            // [4] = PDFOperator{"k"}
            assertEquals(0.1f, ((COSNumber)pageTokens.get(0)).floatValue());
            assertEquals(0.2f, ((COSNumber)pageTokens.get(1)).floatValue());
            assertEquals(0.3f, ((COSNumber)pageTokens.get(2)).floatValue());
            assertEquals(0.4f, ((COSNumber)pageTokens.get(3)).floatValue());
            assertEquals(OperatorName.NON_STROKING_CMYK, ((Operator) pageTokens.get(4)).getName());

            // same as above but for PDPageContentStream#setStrokingColor
            page = new PDPage();
            doc.addPage(page);

            try ( PDPageContentStream contentStream = new PDPageContentStream(doc, page, AppendMode.OVERWRITE, false))
            {
                // pass a stroking color in CMYK color space
                contentStream.setStrokingColor(0.5f, 0.6f, 0.7f, 0.8f);

                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setStrokingColor(1.1f, 0, 0, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setStrokingColor(0, 1.1f, 0, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setStrokingColor(0, 0, 1.1f, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setStrokingColor(0, 0, 0, 1.1f));
            }

            // now read the PDF stream and verify that the CMYK values are correct
            parser = new PDFStreamParser(page);
            pageTokens = parser.parse();
            // expected five tokens  :
            // [0] = COSFloat{0.5}
            // [1] = COSFloat{0.6}
            // [2] = COSFloat{0.7}
            // [3] = COSFloat{0.8}
            // [4] = PDFOperator{"K"}
            assertEquals(0.5f, ((COSNumber) pageTokens.get(0)).floatValue());
            assertEquals(0.6f, ((COSNumber) pageTokens.get(1)).floatValue());
            assertEquals(0.7f, ((COSNumber) pageTokens.get(2)).floatValue());
            assertEquals(0.8f, ((COSNumber) pageTokens.get(3)).floatValue());
            assertEquals(OperatorName.STROKING_COLOR_CMYK, ((Operator)pageTokens.get(4)).getName());
        }
    }

    @Test
    public void testSetRGBandGColors() throws IOException
    {
        try (PDDocument doc = new PDDocument())
        {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page, AppendMode.OVERWRITE, true))
            {
                // pass a non-stroking color in RGB and Gray color space
                contentStream.setNonStrokingColor(0.1f, 0.2f, 0.3f);
                contentStream.setNonStrokingColor(0.8f);

                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setNonStrokingColor(1.1f, 0, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setNonStrokingColor(0, 1.1f, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setNonStrokingColor(0, 0, 1.1f));

                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setNonStrokingColor(1.1f));
            }

            // now read the PDF stream and verify that the values are correct
            PDFStreamParser parser = new PDFStreamParser(page);
            List<Object> pageTokens = parser.parse();
            assertEquals(0.1f, ((COSNumber) pageTokens.get(0)).floatValue());
            assertEquals(0.2f, ((COSNumber) pageTokens.get(1)).floatValue());
            assertEquals(0.3f, ((COSNumber) pageTokens.get(2)).floatValue());
            assertEquals(OperatorName.NON_STROKING_RGB, ((Operator) pageTokens.get(3)).getName());
            assertEquals(0.8f, ((COSNumber) pageTokens.get(4)).floatValue());
            assertEquals(OperatorName.NON_STROKING_GRAY, ((Operator) pageTokens.get(5)).getName());

            // same as above but for PDPageContentStream#setStrokingColor
            page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page, AppendMode.OVERWRITE, false))
            {
                // pass a stroking color in RGB and Gray color space
                contentStream.setStrokingColor(0.5f, 0.6f, 0.7f);
                contentStream.setStrokingColor(0.8f);

                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setStrokingColor(1.1f, 0, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setStrokingColor(0, 1.1f, 0));
                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setStrokingColor(0, 0, 1.1f));

                Assert.assertThrows(IllegalArgumentException.class,
                        () -> contentStream.setStrokingColor(1.1f));
            }

            // now read the PDF stream and verify that the values are correct
            parser = new PDFStreamParser(page);
            pageTokens = parser.parse();
            assertEquals(0.5f, ((COSNumber) pageTokens.get(0)).floatValue());
            assertEquals(0.6f, ((COSNumber) pageTokens.get(1)).floatValue());
            assertEquals(0.7f, ((COSNumber) pageTokens.get(2)).floatValue());
            assertEquals(OperatorName.STROKING_COLOR_RGB, ((Operator) pageTokens.get(3)).getName());
            assertEquals(0.8f, ((COSNumber) pageTokens.get(4)).floatValue());
            assertEquals(OperatorName.STROKING_COLOR_GRAY, ((Operator) pageTokens.get(5)).getName());
        }
    }

    /**
     * PDFBOX-3510: missing content stream should not fail.
     *
     * @throws IOException
     */
    @Test
    public void testMissingContentStream() throws IOException
    {
        PDPage page = new PDPage();
        PDFStreamParser parser = new PDFStreamParser(page);
        List<Object> tokens = parser.parse();
        assertEquals(0, tokens.size());
    }

    /**
     * Check that close() can be called twice.
     *
     * @throws IOException
     */
    @Test
    public void testCloseContract() throws IOException
    {
        try (PDDocument doc = new PDDocument())
        {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(doc, page, AppendMode.OVERWRITE, true);
            contentStream.close();
            contentStream.close();
        }
    }

    /**
     * Check that general graphics state operators are allowed in text mode.
     *
     * @throws IOException
     */
    @Test
    public void testGeneralGraphicStateOperatorTextMode() throws IOException
    {
        try (PDDocument doc = new PDDocument())
        {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(doc, page);
            contentStream.beginText();

            PDImageXObject img1 = new PDImageXObject(doc);
            PDInlineImage img2 = new PDInlineImage(new COSDictionary(), new byte[0], new PDResources());
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.drawImage(img1, 0f, 0f, 1f, 1f));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.drawImage(img1, new Matrix()));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.drawImage(img2, 0f, 0f, 1f, 1f));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.addRect(0, 0, 1, 1));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.curveTo(0, 0, 1, 1, 2, 2));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.curveTo1(0, 0, 1, 1));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.curveTo2(0, 0, 1, 1));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.moveTo(0, 0));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.lineTo(1, 1));
            Assert.assertThrows(IllegalStateException.class,
                    () -> contentStream.shadingFill(new PDShadingType1(new COSDictionary())));
            Assert.assertThrows(IllegalStateException.class, contentStream::stroke);
            Assert.assertThrows(IllegalStateException.class, contentStream::closeAndStroke);
            Assert.assertThrows(IllegalStateException.class, contentStream::closeAndFillAndStroke);
            Assert.assertThrows(IllegalStateException.class, contentStream::closeAndFillAndStrokeEvenOdd);
            Assert.assertThrows(IllegalStateException.class, contentStream::fill);
            Assert.assertThrows(IllegalStateException.class, contentStream::fillAndStroke);
            Assert.assertThrows(IllegalStateException.class, contentStream::fillAndStrokeEvenOdd);
            Assert.assertThrows(IllegalStateException.class, contentStream::fillEvenOdd);
            Assert.assertThrows(IllegalStateException.class, contentStream::closePath);
            Assert.assertThrows(IllegalStateException.class, contentStream::clip);
            Assert.assertThrows(IllegalStateException.class, contentStream::clipEvenOdd);

            // J
            contentStream.setLineCapStyle(0);
            // j
            contentStream.setLineJoinStyle(0);
            // w
            contentStream.setLineWidth(10f);
            // d
            contentStream.setLineDashPattern(new float[] { 2, 1 }, 0f);
            // M
            contentStream.setMiterLimit(1.0f);
            // gs
            contentStream.setGraphicsStateParameters(new PDExtendedGraphicsState());
            // ri, i are not supported with a specific setter
            contentStream.endText();
            contentStream.close();
        }
        catch (IllegalArgumentException exception)
        {

            fail(exception.getLocalizedMessage());
        }
    }

}
