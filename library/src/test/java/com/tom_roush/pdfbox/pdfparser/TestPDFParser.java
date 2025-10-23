/*****************************************************************************
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 ****************************************************************************/

package com.tom_roush.pdfbox.pdfparser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;

import com.tom_roush.pdfbox.Loader;
import com.tom_roush.pdfbox.cos.COSDocument;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.io.RandomAccessRead;
import com.tom_roush.pdfbox.io.ScratchFile;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.util.DateConverter;
import com.tom_roush.tools.FileTools;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(RobolectricTestRunner.class)
public class TestPDFParser
{

    @Test
    public void testPDFParserMissingCatalog() throws URISyntaxException
    {
        // PDFBOX-3060
        try
        {
            Loader.loadPDF(new File(TestPDFParser.class. getResource("/pdfbox/com/tom_roush/pdfbox/pdfparser/MissingCatalog.pdf").toURI())).close();
        }
        catch (Exception exception)
        {
            fail("Unexpected Exception: " + exception.getLocalizedMessage());
        }
    }

    /**
     * Test whether /Info dictionary is retrieved correctly when rebuilding the trailer of a corrupt
     * file. An incorrect algorithm would result in an outline dictionary being mistaken for an
     * /Info.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox3208() throws IOException
    {
        var pdfFile = FileTools.getInternetFile("https://issues.apache.org/jira/secure/attachment/12784025/PDFBOX-3208-L33MUTT2SVCWGCS6UIYL5TH3PNPXHIS6.pdf", "PDFBOX-3208-L33MUTT2SVCWGCS6UIYL5TH3PNPXHIS6.pdf");
        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            PDDocumentInformation di = doc.getDocumentInformation();
            assertEquals("Liquent Enterprise Services", di.getAuthor());
            assertEquals("Liquent services server", di.getCreator());
            assertEquals("Amyuni PDF Converter version 4.0.0.9", di.getProducer());
            assertEquals("", di.getKeywords());
            assertEquals("", di.getSubject());
            assertEquals("892B77DE781B4E71A1BEFB81A51A5ABC_20140326022424.docx", di.getTitle());
            assertEquals(DateConverter.toCalendar("D:20140326142505-02'00'"), di.getCreationDate());
            assertEquals(DateConverter.toCalendar("20140326172513Z"), di.getModificationDate());
        }
    }

    /**
     * Test whether the /Info is retrieved correctly when rebuilding the trailer of a corrupt file,
     * despite the /Info dictionary not having a modification date.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox3940() throws IOException
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12888957/079977.pdf";
        var fileName = "PDFBOX-3940-079977.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            PDDocumentInformation di = doc.getDocumentInformation();
            assertEquals("Unknown", di.getAuthor());
            assertEquals("C:REGULA~1IREGSFR_EQ_EM.WP", di.getCreator());
            assertEquals("Acrobat PDFWriter 3.02 for Windows", di.getProducer());
            assertEquals("", di.getKeywords());
            assertEquals("", di.getSubject());
            assertEquals("C:REGULA~1IREGSFR_EQ_EM.PDF", di.getTitle());
            assertEquals(DateConverter.toCalendar("Tuesday, July 28, 1998 4:00:09 PM"), di.getCreationDate());
        }
    }

    /**
     * PDFBOX-3783: test parsing of file with trash after %%EOF.
     */
    @Test
    public void testPDFBox3783()
    {
        var pdfFile = FileTools.getInternetFile("https://issues.apache.org/jira/secure/attachment/12867102/PDFBOX-3783-72GLBIGUC6LB46ELZFBARRJTLN4RBSQM.pdf", "PDFBOX-3783-72GLBIGUC6LB46ELZFBARRJTLN4RBSQM.pdf");
        try
        {
            Loader.loadPDF(pdfFile).close();
        }
        catch (Exception exception)
        {
            fail("Unexpected IOException");
        }

    }

    /**
     * PDFBOX-3785, PDFBOX-3957:
     * Test whether truncated file with several revisions has correct page count.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox3785() throws IOException
    {
        var pdfFile = FileTools.getInternetFile("https://issues.apache.org/jira/secure/attachment/12867113/202097.pdf", "PDFBOX-3785-202097.pdf");

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            assertEquals(11, doc.getNumberOfPages());
        }
    }

    /**
     * PDFBOX-3947: test parsing of file with broken object stream.
     */
    @Test
    public void testPDFBox3947()
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12890031/670064.pdf";
        var fileName = "PDFBOX-3947-670064.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);
        try
        {
            Loader.loadPDF(pdfFile).close();
        }
        catch (Exception exception)
        {
            fail("Unexpected Exception");
        }
    }

    /**
     * PDFBOX-3948: test parsing of file with object stream containing some unexpected newlines.
     */
    @Test
    public void testPDFBox3948()
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12890034/EUWO6SQS5TM4VGOMRD3FLXZHU35V2CP2.pdf";
        var fileName = "PDFBOX-3948-EUWO6SQS5TM4VGOMRD3FLXZHU35V2CP2.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);
        try
        {
            Loader.loadPDF(pdfFile).close();
        }
        catch (Exception exception)
        {
            fail("Unexpected Exception");
        }
    }

    /**
     * PDFBOX-3949: test parsing of file with incomplete object stream.
     */
    @Test
    public void testPDFBox3949()
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12890037/MKFYUGZWS3OPXLLVU2Z4LWCTVA5WNOGF.pdf";
        var fileName = "PDFBOX-3949-MKFYUGZWS3OPXLLVU2Z4LWCTVA5WNOGF.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);
        try
        {
            Loader.loadPDF(pdfFile).close();
        }
        catch (Exception exception)
        {
            fail("Unexpected Exception");
        }
    }

    /**
     * PDFBOX-3950: test parsing and rendering of truncated file with missing pages.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox3950() throws IOException
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12890042/23EGDHXSBBYQLKYOKGZUOVYVNE675PRD.pdf";
        var fileName = "PDFBOX-3950-23EGDHXSBBYQLKYOKGZUOVYVNE675PRD.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            assertEquals(4, doc.getNumberOfPages());
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); ++i)
            {
                try
                {
                    renderer.renderImage(i);
                }
                catch (IOException ex)
                {
                    if (i == 3 && ex.getMessage().equals("Missing descendant font array"))
                    {
                        continue;
                    }
                    throw ex;
                }
            }
        }
    }

    /**
     * PDFBOX-3951: test parsing of truncated file.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox3951() throws IOException
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12890047/FIHUZWDDL2VGPOE34N6YHWSIGSH5LVGZ.pdf";
        var fileName = "PDFBOX-3951-FIHUZWDDL2VGPOE34N6YHWSIGSH5LVGZ.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            assertEquals(143, doc.getNumberOfPages());
        }
    }

    /**
     * PDFBOX-3964: test parsing of broken file.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox3964() throws IOException
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12892097/c687766d68ac766be3f02aaec5e0d713_2.pdf";
        var fileName = "PDFBOX-3964-c687766d68ac766be3f02aaec5e0d713_2.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            assertEquals(10, doc.getNumberOfPages());
        }
    }

    /**
     * Test whether /Info dictionary is retrieved correctly in brute force search for the
     * Info/Catalog dictionaries.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox3977() throws IOException
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12893582/63NGFQRI44HQNPIPEJH5W2TBM6DJZWMI.pdf";
        var fileName = "PDFBOX-3977-63NGFQRI44HQNPIPEJH5W2TBM6DJZWMI.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            PDDocumentInformation di = doc.getDocumentInformation();
            assertEquals("QuarkXPress(tm) 6.52", di.getCreator());
            assertEquals("Acrobat Distiller 7.0 pour Macintosh", di.getProducer());
            assertEquals("Fich sal Fabr corr1 (Page 6)", di.getTitle());
            assertEquals(DateConverter.toCalendar("D:20070608151915+02'00'"), di.getCreationDate());
            assertEquals(DateConverter.toCalendar("D:20080604152122+02'00'"), di.getModificationDate());
        }
    }

    /**
     * Test parsing the "genko_oc_shiryo1.pdf" file, which is susceptible to regression.
     */
    @Test
    public void testParseGenko()
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12867433/genko_oc_shiryo1.pdf";
        var fileName = "genko_oc_shiryo1.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try
        {
            Loader.loadPDF(pdfFile).close();
        }
        catch (Exception exception)
        {
            fail("Unexpected Exception");
        }
    }

    /**
     * Test parsing the file from PDFBOX-4338, which brought an
     * ArrayIndexOutOfBoundsException before the bug was fixed.
     */
    @Test
    public void testPDFBox4338()
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12943502/ArrayIndexOutOfBoundsException%20COSParser";
        var fileName = "PDFBOX-4338.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try
        {
            Loader.loadPDF(pdfFile).close();
        }
        catch (Exception exception)
        {
            fail("Unexpected Exception");
        }
    }

    /**
     * Test parsing the file from PDFBOX-4339, which brought a
     * NullPointerException before the bug was fixed.
     */
    @Test
    public void testPDFBox4339()
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12943503/NullPointerException%20COSParser";
        var fileName = "PDFBOX-4339.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try
        {
            Loader.loadPDF(pdfFile).close();
        }
        catch (Exception exception)
        {
            fail("Unexpected Exception");
        }
    }

    /**
     * Test parsing the "WXMDXCYRWFDCMOSFQJ5OAJIAFXYRZ5OA.pdf" file, which is susceptible to
     * regression.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox4153() throws IOException
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12914331/WXMDXCYRWFDCMOSFQJ5OAJIAFXYRZ5OA.pdf";
        var fileName = "PDFBOX-4153-WXMDXCYRWFDCMOSFQJ5OAJIAFXYRZ5OA.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            PDDocumentOutline documentOutline = doc.getDocumentCatalog().getDocumentOutline();
            PDOutlineItem firstChild = documentOutline.getFirstChild();
            assertEquals("Main Menu", firstChild.getTitle());
        }
    }

    /**
     * Test that PDFBOX-4490 has 3 pages.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox4490() throws IOException
    {
        var url = "https://issues.apache.org/jira/secure/attachment/12962991/NeS1078.pdf";
        var fileName = "PDFBOX-4490.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            assertEquals(3, doc.getNumberOfPages());
        }
    }

    /**
     * PDFBOX-5025: Test for "74191endobj"
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox5025() throws IOException
    {
        var url = "https://issues.apache.org/jira/secure/attachment/13015946/issue3323.pdf";
        var fileName = "PDFBOX-5025.pdf";
        var pdfFile = FileTools.getInternetFile(url, fileName);

        try (PDDocument doc = Loader.loadPDF(pdfFile))
        {
            assertEquals(1, doc.getNumberOfPages());
            PDFont font = doc.getPage(0).getResources().getFont(COSName.getPDFName("F1"));
            int length1 = font.getFontDescriptor().getFontFile2().getCOSObject().getInt(COSName.LENGTH1);
            assertEquals(74191, length1);
        }
    }
}
