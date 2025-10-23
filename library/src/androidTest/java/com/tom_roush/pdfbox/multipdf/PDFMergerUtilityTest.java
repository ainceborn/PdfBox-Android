/*
 * Copyright 2014 The Apache Software Foundation.
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
package com.tom_roush.pdfbox.multipdf;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.tom_roush.pdfbox.Loader;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.android.TestResourceGenerator;
import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSObject;
import com.tom_roush.pdfbox.io.IOUtils;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.io.RandomAccessRead;
import com.tom_roush.pdfbox.io.RandomAccessReadBufferedFile;
import com.tom_roush.pdfbox.io.RandomAccessStreamCache;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageTree;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.COSObjectable;
import com.tom_roush.pdfbox.pdmodel.common.PDNameTreeNode;
import com.tom_roush.pdfbox.pdmodel.common.PDNumberTreeNode;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDParentTreeValue;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDAction;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFMarkedContentExtractor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Test suite for PDFMergerUtility.
 *
 * @author Maruan Sahyoun (PDF files)
 * @author Tilman Hausherr (code)
 */
public class PDFMergerUtilityTest
{
    private static final String SRCDIR = "src/test/resources/input/merge/";
    private static final String TARGETTESTDIR = "target/test-output/merge/";
    private static final File TARGETPDFDIR = new File("target/pdfs");
    private static final int DPI = 96;

    @BeforeClass
    public static void setUp()
    {
        new File(TARGETTESTDIR).mkdirs();
    }

    /**
     * Tests whether the merge of two PDF files with identically named but
     * different global resources works. The two PDF files have two fonts each
     * named /TT1 and /TT0 that are Arial and Courier and vice versa in the
     * second file. Revisions before 1613017 fail this test because global
     * resources were merged which made trouble when resources of the same kind
     * had the same name.
     *
     * @throws IOException if something goes wrong.
     */
    @Test
    public void testPDFMergerUtility() throws IOException
    {
        checkMergeIdentical("PDFBox.GlobalResourceMergeTest.Doc01.decoded.pdf",
                "PDFBox.GlobalResourceMergeTest.Doc02.decoded.pdf",
                "GlobalResourceMergeTestResult1.pdf",
                IOUtils.createMemoryOnlyStreamCache());

        // once again, with scratch file
        checkMergeIdentical("PDFBox.GlobalResourceMergeTest.Doc01.decoded.pdf",
                "PDFBox.GlobalResourceMergeTest.Doc02.decoded.pdf",
                "GlobalResourceMergeTestResult2.pdf",
                IOUtils.createTempFileOnlyStreamCache());
    }

    // see PDFBOX-2893
    @Test
    public void testPDFMergerUtility2() throws IOException
    {
        checkMergeIdentical("PDFBox.GlobalResourceMergeTest.Doc01.pdf",
                "PDFBox.GlobalResourceMergeTest.Doc02.pdf",
                "GlobalResourceMergeTestResult3.pdf",
                IOUtils.createMemoryOnlyStreamCache());

        // once again, with scratch file
        checkMergeIdentical("PDFBox.GlobalResourceMergeTest.Doc01.pdf",
                "PDFBox.GlobalResourceMergeTest.Doc02.pdf",
                "GlobalResourceMergeTestResult4.pdf",
                IOUtils.createTempFileOnlyStreamCache());
    }

    /**
     * Tests whether the merge of two PDF files with JPEG and CCITT works. A few revisions before
     * 1704911 this test failed because the clone utility attempted to decode and re-encode the
     * streams, see PDFBOX-2893 on 23.9.2015.
     *
     * @throws IOException if something goes wrong.
     */
    @Test
    public void testJpegCcitt() throws IOException
    {
        checkMergeIdentical("jpegrgb.pdf",
                "multitiff.pdf",
                "JpegMultiMergeTestResult.pdf",
                IOUtils.createMemoryOnlyStreamCache());

        // once again, with scratch file
        checkMergeIdentical("jpegrgb.pdf",
                "multitiff.pdf",
                "JpegMultiMergeTestResult.pdf",
                IOUtils.createTempFileOnlyStreamCache());
    }

    /**
     * PDFBOX-3972: Test that OpenAction page destination isn't lost after merge.
     *
     * @throws IOException
     */
    @Test
    public void testPDFMergerOpenAction() throws IOException
    {
        try (PDDocument doc1 = new PDDocument())
        {
            doc1.addPage(new PDPage());
            doc1.addPage(new PDPage());
            doc1.addPage(new PDPage());
            doc1.save(new File(TARGETTESTDIR,"MergerOpenActionTest1.pdf"));
        }

        PDPageDestination dest;
        try (PDDocument doc2 = new PDDocument())
        {
            doc2.addPage(new PDPage());
            doc2.addPage(new PDPage());
            doc2.addPage(new PDPage());
            dest = new PDPageFitDestination();
            dest.setPage(doc2.getPage(1));
            doc2.getDocumentCatalog().setOpenAction(dest);
            doc2.save(new File(TARGETTESTDIR,"MergerOpenActionTest2.pdf"));
        }

        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        pdfMergerUtility.addSource(new File(TARGETTESTDIR, "MergerOpenActionTest1.pdf"));
        pdfMergerUtility.addSource(new File(TARGETTESTDIR, "MergerOpenActionTest2.pdf"));
        pdfMergerUtility.setDestinationFileName(TARGETTESTDIR + "MergerOpenActionTestResult.pdf");
        pdfMergerUtility.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());

        try (PDDocument mergedDoc = Loader
                .loadPDF(new File(TARGETTESTDIR, "MergerOpenActionTestResult.pdf")))
        {
            PDDocumentCatalog documentCatalog = mergedDoc.getDocumentCatalog();
            dest = (PDPageDestination) documentCatalog.getOpenAction();
            assertEquals(4, documentCatalog.getPages().indexOf(dest.getPage()));
        }
    }

    /**
     * PDFBOX-3999: check that page entries in the structure tree only reference pages from the page
     * tree, i.e. that no orphan pages exist.
     *
     * @throws IOException
     */
    @Test
    public void testStructureTreeMerge() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument src = Loader
                .loadPDF(new File(TARGETPDFDIR, "PDFBOX-3999-GeneralForbearance.pdf"));

        ElementCounter elementCounter = new ElementCounter();
        elementCounter.walk(src.getDocumentCatalog().getStructureTreeRoot().getK());
        int singleCnt = elementCounter.cnt;
        int singleSetSize = elementCounter.set.size();
        assertEquals(134, singleCnt);
        assertEquals(134, singleSetSize);

        PDDocument dst = Loader
                .loadPDF(new File(TARGETPDFDIR, "PDFBOX-3999-GeneralForbearance.pdf"));
        pdfMergerUtility.appendDocument(dst, src);
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-3999-GeneralForbearance-merged.pdf"));
        dst.close();

        PDDocument doc = Loader
                .loadPDF(new File(TARGETTESTDIR, "PDFBOX-3999-GeneralForbearance-merged.pdf"));

        // Assume that the merged tree has double element count
        elementCounter = new ElementCounter();
        elementCounter.walk(doc.getDocumentCatalog().getStructureTreeRoot().getK());
        assertEquals(singleCnt * 2, elementCounter.cnt);
        assertEquals(singleSetSize * 2, elementCounter.set.size());
        checkForPageOrphans(doc);

        doc.close();
    }

    /**
     * PDFBOX-3999: check that no streams are kept from the source document by the destination
     * document, despite orphan annotations remaining in the structure tree.
     *
     * @throws IOException
     */
    @Test
    public void testStructureTreeMerge2() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument doc = Loader
                .loadPDF(new File(TARGETPDFDIR, "PDFBOX-3999-GeneralForbearance.pdf"));
        doc.getDocumentCatalog().getAcroForm().flatten();
        doc.save(new File(TARGETTESTDIR, "PDFBOX-3999-GeneralForbearance-flattened.pdf"));

        ElementCounter elementCounter = new ElementCounter();
        elementCounter.walk(doc.getDocumentCatalog().getStructureTreeRoot().getK());
        int singleCnt = elementCounter.cnt;
        int singleSetSize = elementCounter.set.size();
        assertEquals(134, singleCnt);
        assertEquals(134, singleSetSize);

        doc.close();

        PDDocument src = Loader
                .loadPDF(new File(TARGETTESTDIR, "PDFBOX-3999-GeneralForbearance-flattened.pdf"));
        PDDocument dst = Loader
                .loadPDF(new File(TARGETTESTDIR, "PDFBOX-3999-GeneralForbearance-flattened.pdf"));
        pdfMergerUtility.appendDocument(dst, src);
        // before solving PDFBOX-3999, the close() below brought
        // IOException: COSStream has been closed and cannot be read.
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-3999-GeneralForbearance-flattened-merged.pdf"));
        dst.close();

        doc = Loader.loadPDF(
                new File(TARGETTESTDIR, "PDFBOX-3999-GeneralForbearance-flattened-merged.pdf"));

        checkForPageOrphans(doc);

        // Assume that the merged tree has double element count
        elementCounter = new ElementCounter();
        elementCounter.walk(doc.getDocumentCatalog().getStructureTreeRoot().getK());
        assertEquals(singleCnt * 2, elementCounter.cnt);
        assertEquals(singleSetSize * 2, elementCounter.set.size());

        doc.close();
    }

    /**
     * PDFBOX-4408: Check that /StructParents values from pages and /StructParent values from
     * annotations are found in the /ParentTree.
     *
     * @throws IOException
     */
    @Test
    public void testStructureTreeMerge3() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument src = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4408.pdf"));

        ElementCounter elementCounter = new ElementCounter();
        elementCounter.walk(src.getDocumentCatalog().getStructureTreeRoot().getK());
        int singleCnt = elementCounter.cnt;
        int singleSetSize = elementCounter.set.size();
        assertEquals(25, singleCnt);
        assertEquals(25, singleSetSize);

        PDDocument dst = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4408.pdf"));
        pdfMergerUtility.appendDocument(dst, src);
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-4408-merged.pdf"));
        dst.close();

        dst = Loader.loadPDF(new File(TARGETTESTDIR, "PDFBOX-4408-merged.pdf"));

        // Assume that the merged tree has double element count
        elementCounter = new ElementCounter();
        elementCounter.walk(dst.getDocumentCatalog().getStructureTreeRoot().getK());
        assertEquals(singleCnt * 2, elementCounter.cnt);
        assertEquals(singleSetSize * 2, elementCounter.set.size());

        checkWithNumberTree(dst);
        checkForPageOrphans(dst);
        dst.close();
        checkStructTreeRootCount(new File(TARGETTESTDIR, "PDFBOX-4408-merged.pdf"));
    }

    /**
     * PDFBOX-4417: Same as the previous tests, but this one failed when the previous tests
     * succeeded because of more bugs with cloning.
     *
     * @throws IOException
     */
    @Test
    public void testStructureTreeMerge4() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument src = Loader.loadPDF(new File(SRCDIR, "PDFBOX-4417-001031.pdf"));

        ElementCounter elementCounter = new ElementCounter();
        elementCounter.walk(src.getDocumentCatalog().getStructureTreeRoot().getK());
        int singleCnt = elementCounter.cnt;
        int singleSetSize = elementCounter.set.size();
        assertEquals(104, singleCnt);
        assertEquals(104, singleSetSize);

        PDDocument dst = Loader.loadPDF(new File(SRCDIR, "PDFBOX-4417-001031.pdf"));
        pdfMergerUtility.appendDocument(dst, src);
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-4417-001031-merged.pdf"));
        dst.close();
        dst = Loader.loadPDF(new File(TARGETTESTDIR, "PDFBOX-4417-001031-merged.pdf"));

        // Assume that the merged tree has double element count
        elementCounter = new ElementCounter();
        elementCounter.walk(dst.getDocumentCatalog().getStructureTreeRoot().getK());
        assertEquals(singleCnt * 2, elementCounter.cnt);
        assertEquals(singleSetSize * 2, elementCounter.set.size());

        checkWithNumberTree(dst);
        checkForPageOrphans(dst);
        dst.close();
        checkStructTreeRootCount(new File(TARGETTESTDIR, "PDFBOX-4417-001031-merged.pdf"));
    }

    /**
     * PDFBOX-4417: Same as the previous tests, but this one failed when the previous tests
     * succeeded because the /K tree started with two dictionaries and not with an array.
     *
     * @throws IOException
     */
    @Test
    public void testStructureTreeMerge5() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument src = Loader.loadPDF(new File(SRCDIR, "PDFBOX-4417-054080.pdf"));

        ElementCounter elementCounter = new ElementCounter();
        elementCounter.walk(src.getDocumentCatalog().getStructureTreeRoot().getK());
        int singleCnt = elementCounter.cnt;
        int singleSetSize = elementCounter.set.size();

        PDDocument dst = Loader.loadPDF(new File(SRCDIR, "PDFBOX-4417-054080.pdf"));
        pdfMergerUtility.appendDocument(dst, src);
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-4417-054080-merged.pdf"));
        dst.close();
        dst = Loader.loadPDF(new File(TARGETTESTDIR, "PDFBOX-4417-054080-merged.pdf"));
        checkWithNumberTree(dst);
        checkForPageOrphans(dst);

        // Assume that the merged tree has double element count
        elementCounter = new ElementCounter();
        elementCounter.walk(dst.getDocumentCatalog().getStructureTreeRoot().getK());
        assertEquals(singleCnt * 2, elementCounter.cnt);
        assertEquals(singleSetSize * 2, elementCounter.set.size());

        dst.close();

        checkStructTreeRootCount(new File(TARGETTESTDIR, "PDFBOX-4417-054080-merged.pdf"));
    }

    /**
     * PDFBOX-4418: test merging PDFs where ParentTree have a hierarchy.
     *
     * @throws IOException
     */
    @Test
    public void testStructureTreeMerge6() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument src = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4418-000671.pdf"));

        PDStructureTreeRoot structureTreeRoot = src.getDocumentCatalog().getStructureTreeRoot();
        PDNumberTreeNode parentTree = structureTreeRoot.getParentTree();
        Map<Integer, COSObjectable> numberTreeAsMap = PDFMergerUtility.getNumberTreeAsMap(parentTree);
        assertEquals(381, numberTreeAsMap.size());
        assertEquals(743, Collections.max(numberTreeAsMap.keySet()) + 1);
        assertEquals(0, (int) Collections.min(numberTreeAsMap.keySet()));
        assertEquals(743, structureTreeRoot.getParentTreeNextKey());

        PDDocument dst = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4418-000314.pdf"));

        structureTreeRoot = dst.getDocumentCatalog().getStructureTreeRoot();
        parentTree = structureTreeRoot.getParentTree();
        numberTreeAsMap = PDFMergerUtility.getNumberTreeAsMap(parentTree);
        assertEquals(7, numberTreeAsMap.size());
        assertEquals(328, Collections.max(numberTreeAsMap.keySet()) + 1);
        assertEquals(321, (int) Collections.min(numberTreeAsMap.keySet()));
        // ParentTreeNextKey should be 321 but PDF has a higher value
        assertEquals(408, structureTreeRoot.getParentTreeNextKey());

        pdfMergerUtility.appendDocument(dst, src);
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-4418-merged.pdf"));
        dst.close();

        dst = Loader.loadPDF(new File(TARGETTESTDIR, "PDFBOX-4418-merged.pdf"));
        checkWithNumberTree(dst);
        checkForPageOrphans(dst);

        structureTreeRoot = dst.getDocumentCatalog().getStructureTreeRoot();
        parentTree = structureTreeRoot.getParentTree();
        numberTreeAsMap = PDFMergerUtility.getNumberTreeAsMap(parentTree);
        assertEquals(381+7, numberTreeAsMap.size());
        assertEquals(408+743, Collections.max(numberTreeAsMap.keySet()) + 1);
        assertEquals(321, (int) Collections.min(numberTreeAsMap.keySet()));
        assertEquals(408+743, structureTreeRoot.getParentTreeNextKey());
        dst.close();

        checkStructTreeRootCount(new File(TARGETTESTDIR, "PDFBOX-4418-merged.pdf"));
    }

    /**
     * PDFBOX-4423: test merging a PDF where a widget has no StructParent.
     *
     * @throws IOException
     */
    @Test
    public void testStructureTreeMerge7() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument src = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4423-000746.pdf"));

        PDStructureTreeRoot structureTreeRoot = src.getDocumentCatalog().getStructureTreeRoot();
        PDNumberTreeNode parentTree = structureTreeRoot.getParentTree();
        Map<Integer, COSObjectable> numberTreeAsMap = PDFMergerUtility.getNumberTreeAsMap(parentTree);
        assertEquals(33, numberTreeAsMap.size());
        assertEquals(64, Collections.max(numberTreeAsMap.keySet()) + 1);
        assertEquals(31, (int) Collections.min(numberTreeAsMap.keySet()));
        assertEquals(126, structureTreeRoot.getParentTreeNextKey());

        PDDocument dst = new PDDocument();

        pdfMergerUtility.appendDocument(dst, src);
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-4423-merged.pdf"));
        dst.close();

        dst = Loader.loadPDF(new File(TARGETTESTDIR, "PDFBOX-4423-merged.pdf"));
        checkWithNumberTree(dst);
        checkForPageOrphans(dst);

        structureTreeRoot = dst.getDocumentCatalog().getStructureTreeRoot();
        parentTree = structureTreeRoot.getParentTree();
        numberTreeAsMap = PDFMergerUtility.getNumberTreeAsMap(parentTree);
        assertEquals(33, numberTreeAsMap.size());
        assertEquals(64, Collections.max(numberTreeAsMap.keySet()) + 1);
        assertEquals(31, (int) Collections.min(numberTreeAsMap.keySet()));
        assertEquals(64, structureTreeRoot.getParentTreeNextKey());
        dst.close();

        checkStructTreeRootCount(new File(TARGETTESTDIR, "PDFBOX-4423-merged.pdf"));
    }

    /**
     * PDFBOX-4009: Test that ParentTreeNextKey is recalculated correctly.
     */
    @Test
    public void testMissingParentTreeNextKey() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument src = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4418-000314.pdf"));
        PDDocument dst = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4418-000314.pdf"));
        // existing numbers are 321..327; ParentTreeNextKey is 408.
        // After deletion, it is recalculated in the merge 328.
        // That value is added to all numbers of the destination,
        // so the new numbers should be 321+328..327+328, i.e. 649..655,
        // and this ParentTreeNextKey is 656 at the end.
        dst.getDocumentCatalog().getStructureTreeRoot().getCOSObject().removeItem(COSName.PARENT_TREE_NEXT_KEY);
        pdfMergerUtility.appendDocument(dst, src);
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-4418-000314-merged.pdf"));
        dst.close();
        dst = Loader.loadPDF(new File(TARGETTESTDIR, "PDFBOX-4418-000314-merged.pdf"));
        assertEquals(656, dst.getDocumentCatalog().getStructureTreeRoot().getParentTreeNextKey());
        dst.close();
    }

    /**
     * PDFBOX-4416: Test merging of /IDTree
     * <br>
     * PDFBOX-4009: test merging to empty destination
     *
     * @throws IOException
     */
    @Test
    public void testStructureTreeMergeIDTree() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        PDDocument src = Loader.loadPDF(new File(SRCDIR, "PDFBOX-4417-001031.pdf"));
        PDDocument dst = Loader.loadPDF(new File(SRCDIR, "PDFBOX-4417-054080.pdf"));

        PDNameTreeNode<PDStructureElement> srcIDTree = src.getDocumentCatalog().getStructureTreeRoot().getIDTree();
        Map<String, PDStructureElement> srcIDTreeMap = PDFMergerUtility.getIDTreeAsMap(srcIDTree);
        PDNameTreeNode<PDStructureElement> dstIDTree = dst.getDocumentCatalog().getStructureTreeRoot().getIDTree();
        Map<String, PDStructureElement> dstIDTreeMap = PDFMergerUtility.getIDTreeAsMap(dstIDTree);
        int expectedTotal = srcIDTreeMap.size() + dstIDTreeMap.size();
        assertEquals(192, expectedTotal);

        // PDFBOX-4009, test that empty dest doc still merges structure tree
        // (empty dest doc is used in command line app)
        PDDocument emptyDest = new PDDocument();
        pdfMergerUtility.appendDocument(emptyDest, src);
        src.close();
        src = emptyDest;
        assertEquals(4, src.getDocumentCatalog().getStructureTreeRoot().getParentTreeNextKey());

        pdfMergerUtility.appendDocument(dst, src);
        src.close();
        dst.save(new File(TARGETTESTDIR, "PDFBOX-4416-IDTree-merged.pdf"));
        dst.close();
        dst = Loader.loadPDF(new File(TARGETTESTDIR, "PDFBOX-4416-IDTree-merged.pdf"));
        checkWithNumberTree(dst);
        checkForPageOrphans(dst);

        dstIDTree = dst.getDocumentCatalog().getStructureTreeRoot().getIDTree();
        dstIDTreeMap = PDFMergerUtility.getIDTreeAsMap(dstIDTree);
        assertEquals(expectedTotal, dstIDTreeMap.size());

        dst.close();
        checkStructTreeRootCount(new File(TARGETTESTDIR, "PDFBOX-4416-IDTree-merged.pdf"));
    }

    /**
     * PDFBOX-4429: merge into destination that has /StructParent(s) entries in the destination file
     * but no structure tree.
     *
     * @throws IOException
     */
    @Test
    public void testMergeBogusStructParents1() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        try (PDDocument src = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4408.pdf"));
             PDDocument dst = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4408.pdf")))
        {
            dst.getDocumentCatalog().setStructureTreeRoot(null);
            dst.getPage(0).setStructParents(9999);
            dst.getPage(0).getAnnotations().get(0).setStructParent(9998);
            pdfMergerUtility.appendDocument(dst, src);
            checkWithNumberTree(dst);
            checkForPageOrphans(dst);
        }
    }

    /**
     * PDFBOX-4429: merge into destination that has /StructParent(s) entries in the source file but
     * no structure tree.
     *
     * @throws IOException
     */
    @Test
    public void testMergeBogusStructParents2() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        try (PDDocument src = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4408.pdf"));
             PDDocument dst = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-4408.pdf")))
        {
            src.getDocumentCatalog().setStructureTreeRoot(null);
            src.getPage(0).setStructParents(9999);
            src.getPage(0).getAnnotations().get(0).setStructParent(9998);
            pdfMergerUtility.appendDocument(dst, src);
            checkWithNumberTree(dst);
            checkForPageOrphans(dst);
        }
    }

    /**
     * Test of the parent tree. Didn't work before PDFBOX-4003 because of incompatible class for
     * PDNumberTreeNode.
     *
     * @throws IOException
     */
    @Test
    public void testParentTree() throws IOException
    {
        try (PDDocument doc = Loader
                .loadPDF(new File(TARGETPDFDIR, "PDFBOX-3999-GeneralForbearance.pdf")))
        {
            PDStructureTreeRoot structureTreeRoot = doc.getDocumentCatalog().getStructureTreeRoot();
            PDNumberTreeNode parentTree = structureTreeRoot.getParentTree();
            parentTree.getValue(0);
            Map<Integer, COSObjectable> numberTreeAsMap = PDFMergerUtility.getNumberTreeAsMap(parentTree);
            assertEquals(31, numberTreeAsMap.size());
            assertEquals(31, Collections.max(numberTreeAsMap.keySet()) + 1);
            assertEquals(0, (int) Collections.min(numberTreeAsMap.keySet()));
            assertEquals(31, structureTreeRoot.getParentTreeNextKey());
        }
    }

    // PDFBOX-4417: check for multiple /StructTreeRoot entries that was due to
    // incorrect merging of /K entries
    private void checkStructTreeRootCount(File file) throws IOException
    {
        try (PDDocument pdf = Loader.loadPDF(file))
        {
            List<COSObject> structTreeRootObjects = pdf.getDocument().getObjectsByType(COSName.STRUCT_TREE_ROOT);
            assertEquals(
                    file.getPath() + " " + structTreeRootObjects,
                    1,
                    structTreeRootObjects.size()
            );

        }
    }

    /**
     * PDFBOX-4408: Check that /StructParents values from pages and /StructParent values from
     * annotations are found in the /ParentTree.
     * <p>
     * Expanded in 2025 to check that all MCIDs of a page content stream have an entry in the
     * ParentTree.
     *
     * @param document
     */
    void checkWithNumberTree(PDDocument document) throws IOException
    {
        PDDocumentCatalog documentCatalog = document.getDocumentCatalog();
        PDNumberTreeNode parentTree = documentCatalog.getStructureTreeRoot().getParentTree();
        assertNotEquals(-1, documentCatalog.getStructureTreeRoot().getParentTreeNextKey());
        Map<Integer, COSObjectable> numberTreeAsMap = PDFMergerUtility.getNumberTreeAsMap(parentTree);
        Set<Integer> keySet = numberTreeAsMap.keySet();
        PDAcroForm acroForm = documentCatalog.getAcroForm();
        if (acroForm != null)
        {
            for (PDField field : acroForm.getFieldTree())
            {
                for (PDAnnotationWidget widget : field.getWidgets())
                {
                    if (widget.getStructParent() >= 0)
                    {
                        assertTrue("field '" + field.getFullyQualifiedName() + "' /StructParent " +
                                widget.getStructParent() + " missing in /ParentTree",
                            keySet.contains(widget.getStructParent()));
                    }
                }
            }
        }
        PDPageTree pageTree = document.getPages();
        for (PDPage page : pageTree)
        {
            int pageNum = pageTree.indexOf(page) + 1;
            if (page.getStructParents() >= 0)
            {
                assertTrue(
                        "/StructParents " + page.getStructParents() + " from page " + pageNum + " not found in /ParentTree",
                        keySet.contains(page.getStructParents())
                );

                PDParentTreeValue obj = (PDParentTreeValue) numberTreeAsMap.get(page.getStructParents());
                assertTrue(
                        "Expected array in page " + pageNum + ", got " + obj.getClass(),
                        obj.getCOSObject() instanceof COSArray
                );

                COSArray array = (COSArray) obj.getCOSObject();

                PDFMarkedContentExtractor markedContentExtractor = new PDFMarkedContentExtractor();
                markedContentExtractor.processPage(page);
                List<PDMarkedContent> markedContents = markedContentExtractor.getMarkedContents();
                TreeSet<Integer> set = new TreeSet<>();
                for (PDMarkedContent pdMarkedContent : markedContents)
                {
                    COSDictionary pdmcProperties = pdMarkedContent.getProperties();
                    if (pdmcProperties == null)
                    {
                        continue;
                    }
                    int mcid = pdMarkedContent.getMCID();
                    if (mcid >= 0)
                    {
                        // "For a page object (...), the value shall be an array of references
                        // to the parent elements of those marked-content sequences."
                        // this means that the /Pg entry doesn't have to match the page
                        COSDictionary dict = (COSDictionary) array.getObject(mcid);
                        assertNotNull(dict);
                        set.add(mcid);
                        PDStructureElement structureElemen = (PDStructureElement) PDStructureNode.create(dict);
                        List<Object> kids = structureElemen.getKids();
                        boolean found = false;
                        for (Object kid : kids)
                        {
                            if (kid instanceof Integer && ((Integer) kid) == mcid)
                            {
                                found = true;
                                break;
                            }
                            if (kid instanceof PDMarkedContentReference)
                            {
                                PDMarkedContentReference mcr = (PDMarkedContentReference) kid;
                                if (mcid == mcr.getMCID())
                                {
                                    found = true;
                                    if (mcr.getPage() != null)
                                    {
                                        assertEquals(page, mcr.getPage());
                                    }
                                    else
                                    {
                                        assertEquals(page, structureElemen.getPage());
                                    }
                                    break;
                                }
                            }
                        }
                        assertTrue(
                                "page: " + pageNum + ", mcid: " + mcid + " not found",
                                found
                        );

                    }
                }
                // actual count may be larger if last element is null, e.g. PDFBOX-4408
                // set can be empty, see last page of pdf_32000_2008.pdf
                assertTrue(set.isEmpty() || set.last() <= array.size() - 1);
            }
            for (PDAnnotation ann : page.getAnnotations())
            {
                if (ann.getStructParent() >= 0)
                {
                    assertTrue(
                            "/StructParent " + ann.getStructParent() + " missing in /ParentTree",
                            keySet.contains(ann.getStructParent())
                    );
                }
            }
        }

        // might also test image and form dictionaries...
    }

    /**
     * PDFBOX-4383: Test that file can be deleted after merge.
     *
     * @throws IOException
     */
    @Test
    public void testFileDeletion() throws IOException
    {
        File outFile = new File(TARGETTESTDIR, "PDFBOX-4383-result.pdf");

        File inFile1 = new File(TARGETTESTDIR, "PDFBOX-4383-src1.pdf");
        File inFile2 = new File(TARGETTESTDIR, "PDFBOX-4383-src2.pdf");

        createSimpleFile(inFile1);
        createSimpleFile(inFile2);

        try (OutputStream out = new FileOutputStream(outFile);
             // Unrelated: increase test coverage by testing RandomAccessRead
             RandomAccessRead rar1 = new RandomAccessReadBufferedFile(inFile1);
             RandomAccessRead rar2 = new RandomAccessReadBufferedFile(inFile2))
        {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(out);
            assertEquals(out, merger.getDestinationStream());

            merger.addSource(rar1);
            merger.addSource(rar2);

            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
        }

        try (PDDocument doc = Loader.loadPDF(outFile))
        {
            assertEquals(2, doc.getNumberOfPages());
        }

        Files.delete(inFile1.toPath());
        Files.delete(inFile2.toPath());
        Files.delete(outFile.toPath());
    }

    /**
     * Check that there is a top level Document and Parts below in a merge of 2 documents.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox5198_2() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        pdfMergerUtility.addSource(new File(SRCDIR, "PDFA3A.pdf"));
        pdfMergerUtility.addSource(new File(SRCDIR, "PDFA3A.pdf"));
        pdfMergerUtility.setDestinationFileName(TARGETTESTDIR + "PDFA3A-merged2.pdf");
        pdfMergerUtility.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());

        checkParts(new File(TARGETTESTDIR + "PDFA3A-merged2.pdf"));
    }

    /**
     * Check that there is a top level Document and Parts below in a merge of 3 documents.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox5198_3() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        pdfMergerUtility.addSource(new File(SRCDIR, "PDFA3A.pdf"));
        pdfMergerUtility.addSource(new File(SRCDIR, "PDFA3A.pdf"));
        pdfMergerUtility.addSource(new File(SRCDIR, "PDFA3A.pdf"));
        pdfMergerUtility.setDestinationFileName(TARGETTESTDIR + "PDFA3A-merged3.pdf");
        pdfMergerUtility.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());

        checkParts(new File(TARGETTESTDIR + "PDFA3A-merged3.pdf"));
    }

    /**
     * Check that there is a top level Document and Parts below.
     * @param file
     * @throws IOException
     */
    private void checkParts(File file) throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(file))
        {
            PDStructureTreeRoot structureTreeRoot = doc.getDocumentCatalog().getStructureTreeRoot();
            COSDictionary topDict = (COSDictionary) structureTreeRoot.getK();
            assertEquals(COSName.DOCUMENT, topDict.getItem(COSName.S));
            assertEquals(structureTreeRoot.getCOSObject(), topDict.getCOSDictionary(COSName.P));
            COSArray kArray = topDict.getCOSArray(COSName.K);
            assertEquals(doc.getNumberOfPages(), kArray.size());
            for (int i = 0; i < kArray.size(); ++i)
            {
                COSDictionary dict = (COSDictionary) kArray.getObject(i);
                assertEquals(COSName.PART, dict.getItem(COSName.S));
                assertEquals(topDict, dict.getCOSDictionary(COSName.P));
            }
        }
    }

    private void checkForPageOrphans(PDDocument doc) throws IOException
    {
        // check for orphan pages in the StructTreeRoot/K, StructTreeRoot/ParentTree and
        // StructTreeRoot/IDTree trees.
        PDPageTree pageTree = doc.getPages();
        PDStructureTreeRoot structureTreeRoot = doc.getDocumentCatalog().getStructureTreeRoot();
        checkElement(pageTree, structureTreeRoot.getParentTree().getCOSObject(), structureTreeRoot.getCOSObject());
        assertNotNull(structureTreeRoot.getK());
        checkElement(pageTree, structureTreeRoot.getK(), structureTreeRoot.getCOSObject());
        checkForIDTreeOrphans(pageTree, structureTreeRoot);
        checkParentTreeAgainstK(structureTreeRoot);
    }

    private void checkParentTreeAgainstK(PDStructureTreeRoot structureTreeRoot) throws IOException
    {
        // check that elements in the /ParentTree are in the /K tree
        ElementCounter elementCounter = new ElementCounter();
        elementCounter.walk(structureTreeRoot.getK());
        Map<Integer, COSObjectable> numberTreeAsMap = PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot.getParentTree());
        for (Map.Entry<Integer, COSObjectable> entry : numberTreeAsMap.entrySet())
        {
            PDParentTreeValue val = (PDParentTreeValue) entry.getValue(); // array or dictionary
            COSBase base = val.getCOSObject();
            if (base instanceof COSArray)
            {
                COSArray array = (COSArray) base;
                for (int i = 0; i < array.size(); ++i)
                {
                    COSBase arrayElement = array.getObject(i);
                    if (arrayElement instanceof COSDictionary)
                    {
                        assertTrue(
                                "Element " + entry.getKey() + ":" + i + " from /ParentTree missing in /K",
                                elementCounter.set.contains(arrayElement)
                        );
                    }
                }
            }
            // can't check this COSDictionary; ElementsCounter only counts those with a /Pg entry
        }
    }

    private void checkForIDTreeOrphans(PDPageTree pageTree, PDStructureTreeRoot structureTreeRoot)
            throws IOException
    {
        PDNameTreeNode<PDStructureElement> idTree = structureTreeRoot.getIDTree();
        if (idTree == null)
        {
            return;
        }
        Map<String, PDStructureElement> map = PDFMergerUtility.getIDTreeAsMap(idTree);
        for (PDStructureElement element : map.values())
        {
            if (element.getPage() != null)
            {
                checkForPage(pageTree, element);
            }
            if (!element.getKids().isEmpty())
            {
                checkElement(pageTree, element.getCOSObject().getDictionaryObject(COSName.K), element.getCOSObject());
            }
        }
    }

    private void createSimpleFile(File file) throws IOException
    {
        try (PDDocument doc = new PDDocument())
        {
            doc.addPage(new PDPage());
            doc.save(file);
        }
    }

    private class ElementCounter
    {
        int cnt = 0;
        final Set<COSBase> set = new HashSet<>();

        void walk(COSBase base)
        {
            if (base instanceof COSArray)
            {
                for (COSBase base2 : (COSArray) base)
                {
                    if (base2 instanceof COSObject)
                    {
                        base2 = ((COSObject) base2).getObject();
                    }
                    walk(base2);
                }
            }
            else if (base instanceof COSDictionary)
            {
                COSDictionary kdict = (COSDictionary) base;
                if (kdict.containsKey(COSName.PG))
                {
                    ++cnt;
                    set.add(kdict);
                }
                else if (kdict.containsKey(COSName.K))
                {
                    // at least 1 kid with dict with /Pg, /MCID and type /MCR
                    // happens with confidential file from PDFBOX-6009
                    COSArray kidArray = kdict.getCOSArray(COSName.K);
                    if (kidArray != null)
                    {
                        for (int i = 0; i < kidArray.size(); ++i)
                        {
                            COSBase base2 = kidArray.getObject(i);
                            if (base2 instanceof COSDictionary &&
                                    ((COSDictionary) base2).containsKey(COSName.PG) &&
                                    ((COSDictionary) base2).containsKey(COSName.MCID))
                            {
                                ++cnt;
                                set.add(kdict);
                                break;
                            }
                        }
                    }
                }
                if (kdict.containsKey(COSName.K))
                {
                    walk(kdict.getDictionaryObject(COSName.K));
                }
            }
        }
    }

    // Each element can be an array, a dictionary or a number.
    // See PDF specification Table 37 - Entries in a number tree node dictionary
    // See PDF specification Table 322 - Entries in the structure tree root
    // See PDF specification Table 323 - Entries in a structure element dictionary
    // See PDF specification Table 325 – Entries in an object reference dictionary
    // example of file with /Kids: 000153.pdf 000208.pdf 000314.pdf 000359.pdf 000671.pdf
    // from digitalcorpora site
    private void checkElement(PDPageTree pageTree, COSBase base, COSDictionary parentDict) throws IOException
    {
        if (base instanceof COSArray)
        {
            for (COSBase base2 : (COSArray) base)
            {
                if (base2 instanceof COSObject)
                {
                    base2 = ((COSObject) base2).getObject();
                }
                checkElement(pageTree, base2, parentDict);
            }
        }
        else if (base instanceof COSDictionary)
        {
            COSDictionary kdict = (COSDictionary) base;
            if (kdict.containsKey(COSName.PG))
            {
                PDStructureElement structureElement = new PDStructureElement(kdict);
                checkForPage(pageTree, structureElement);
            }
            if (kdict.containsKey(COSName.K))
            {
                checkElement(pageTree, kdict.getDictionaryObject(COSName.K), kdict);

                // Check that the /P entry points to the correct object
                PDStructureNode node = PDStructureNode.create(kdict);
                for (Object obj : node.getKids())
                {
                    if (obj instanceof PDStructureElement)
                    {
                        PDStructureNode parent = ((PDStructureElement) obj).getParent();
                        assertSame(parent.getCOSObject(), kdict);
                    }
                }
                return;
            }

            // if we're in a number tree, check /Nums and /Kids
            if (kdict.containsKey(COSName.KIDS))
            {
                checkElement(pageTree, kdict.getDictionaryObject(COSName.KIDS), kdict);
            }
            else if (kdict.containsKey(COSName.NUMS))
            {
                checkElement(pageTree, kdict.getDictionaryObject(COSName.NUMS), kdict);
            }

            if (COSName.OBJR.equals(kdict.getDictionaryObject(COSName.TYPE)) ||
                    COSName.MCR.equals(kdict.getDictionaryObject(COSName.TYPE)))
            {
                assertFalse(kdict.getCOSDictionary(COSName.PG) == null && parentDict.getCOSDictionary(COSName.PG) == null);
            }

            // if we're an object reference dictionary (/OBJR), check the obj
            if (kdict.containsKey(COSName.OBJ))
            {
                COSDictionary obj = (COSDictionary) kdict.getDictionaryObject(COSName.OBJ);
                COSBase type = obj.getDictionaryObject(COSName.TYPE);
                COSBase subtype = obj.getDictionaryObject(COSName.SUBTYPE);
                if (COSName.ANNOT.equals(type) || COSName.LINK.equals(subtype))
                {
                    PDAnnotation annotation = PDAnnotation.createAnnotation(obj);
                    PDPage page = annotation.getPage();
                    if (annotation instanceof PDAnnotationLink)
                    {
                        // PDFBOX-5928: check whether the destination of a link annotation is an orphan
                        PDAnnotationLink link = (PDAnnotationLink) annotation;
                        PDDestination destination = link.getDestination();
                        if (destination == null)
                        {
                            PDAction action = link.getAction();
                            if (action instanceof PDActionGoTo)
                            {
                                PDActionGoTo goToAction = (PDActionGoTo) action;
                                destination = goToAction.getDestination();
                            }
                        }
                        if (destination instanceof PDPageDestination)
                        {
                            PDPageDestination pageDestination = (PDPageDestination) destination;
                            PDPage destPage = pageDestination.getPage();
                            if (destPage != null)
                            {
                                assertNotEquals(
                                        "Annotation destination page is not in the page tree: " + destPage,
                                        -1,
                                        pageTree.indexOf(destPage)
                                );
                            }
                        }
                    }
                    if (page != null)
                    {
                        if (pageTree.indexOf(page) == -1)
                        {
                            COSBase item = kdict.getItem(COSName.OBJ);
                            if (item instanceof COSObject)
                            {
                                assertNotEquals(
                                        "Annotation page is not in the page tree: " + item,
                                        -1,
                                        pageTree.indexOf(page)
                                );

                            }
                            else
                            {
                                // don't display because of stack overflow
                                assertNotEquals(
                                        "Annotation page is not in the page tree",
                                        -1,
                                        pageTree.indexOf(page)
                                );

                            }
                        }
                    }
                }
                else
                {
                    //TODO needs to be investigated. Specification mentions
                    // "such as an XObject or an annotation"
                    fail("Other type: " + type + ", obj: " + obj);
                }
            }
        }
    }

    // checks that the result file of a merge has the same rendering as the two source files
    private void checkMergeIdentical(String filename1, String filename2, String mergeFilename,
                                     RandomAccessStreamCache.StreamCacheCreateFunction streamCache)
            throws IOException
    {
        int src1PageCount;
        Bitmap[] src1ImageTab;
        try (PDDocument srcDoc1 = Loader.loadPDF(new File(SRCDIR, filename1), (String) null))
        {
            src1PageCount = srcDoc1.getNumberOfPages();
            PDFRenderer src1PdfRenderer = new PDFRenderer(srcDoc1);
            src1ImageTab = new Bitmap[src1PageCount];
            for (int page = 0; page < src1PageCount; ++page)
            {
                src1ImageTab[page] = src1PdfRenderer.renderImageWithDPI(page, DPI);
            }
        }

        int src2PageCount;
        Bitmap[] src2ImageTab;
        try (PDDocument srcDoc2 = Loader.loadPDF(new File(SRCDIR, filename2), (String) null))
        {
            src2PageCount = srcDoc2.getNumberOfPages();
            PDFRenderer src2PdfRenderer = new PDFRenderer(srcDoc2);
            src2ImageTab = new Bitmap[src2PageCount];
            for (int page = 0; page < src2PageCount; ++page)
            {
                src2ImageTab[page] = src2PdfRenderer.renderImageWithDPI(page, DPI);
            }
        }

        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        pdfMergerUtility.addSource(new File(SRCDIR, filename1));
        pdfMergerUtility.addSource(new File(SRCDIR, filename2));
        pdfMergerUtility.setDestinationFileName(TARGETTESTDIR + mergeFilename);
        pdfMergerUtility.mergeDocuments(streamCache);

        try (PDDocument mergedDoc = Loader.loadPDF(new File(TARGETTESTDIR, mergeFilename),
                (String) null))
        {
            PDFRenderer mergePdfRenderer = new PDFRenderer(mergedDoc);
            int mergePageCount = mergedDoc.getNumberOfPages();
            assertEquals(src1PageCount + src2PageCount, mergePageCount);
            for (int page = 0; page < src1PageCount; ++page)
            {
                Bitmap bim = mergePdfRenderer.renderImageWithDPI(page, DPI);
                checkImagesIdentical(bim, src1ImageTab[page]);
            }
            for (int page = 0; page < src2PageCount; ++page)
            {
                int mergePage = page + src1PageCount;
                Bitmap bim = mergePdfRenderer.renderImageWithDPI(mergePage, DPI);
                checkImagesIdentical(bim, src2ImageTab[page]);
            }
        }
    }

    private void checkImagesIdentical(Bitmap bim1, Bitmap bim2)
    {
        assertEquals(bim1.getHeight(), bim2.getHeight());
        assertEquals(bim1.getWidth(), bim2.getWidth());
        int w = bim1.getWidth();
        int h = bim1.getHeight();
        for (int i = 0; i < w; ++i)
        {
            for (int j = 0; j < h; ++j)
            {
                assertEquals(bim1.getPixel(i, j), bim2.getPixel(i, j));
            }
        }
    }

    private void checkForPage(PDPageTree pageTree, PDStructureElement structureElement)
    {
        PDPage page = structureElement.getPage();
        if (page != null)
        {
            assertNotEquals(
                    "Page is not in the page tree",
                    -1,
                    pageTree.indexOf(page)
            );
        }
    }

    @Test
    public void testSplitWithStructureTree() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(SRCDIR, "PDFBOX-4417-001031.pdf")))
        {
            Splitter splitter = new Splitter();
            splitter.setStartPage(1);
            splitter.setEndPage(2);
            splitter.setSplitAtPage(2);
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(1, splitResult.size());
            try (PDDocument dstDoc = splitResult.get(0))
            {
                assertEquals(2, dstDoc.getNumberOfPages());
                checkForPageOrphans(dstDoc);
                // these tests just verify the status quo. Changes should be checked visually with
                // a PDF viewer that can display structural information.
                PDStructureTreeRoot structureTreeRoot = dstDoc.getDocumentCatalog().getStructureTreeRoot();
                assertEquals(126, PDFMergerUtility.getIDTreeAsMap(structureTreeRoot.getIDTree()).size());
                assertEquals(2, PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot.getParentTree()).size());
                assertEquals(6, structureTreeRoot.getRoleMap().size());
            }
        }
    }

    @Test
    public void testSplitWithStructureTreeAndDestinations() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(SRCDIR,"PDFBOX-5762-722238.pdf")))
        {
            Splitter splitter = new Splitter();
            splitter.setStartPage(1);
            splitter.setEndPage(2);
            splitter.setSplitAtPage(2);
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(1, splitResult.size());
            try (PDDocument dstDoc = splitResult.get(0))
            {
                assertEquals(2, dstDoc.getNumberOfPages());
                checkForPageOrphans(dstDoc);
                // these tests just verify the status quo. Changes should be checked visually with
                // a PDF viewer that can display structural information.
                PDStructureTreeRoot structureTreeRoot = dstDoc.getDocumentCatalog().getStructureTreeRoot();
                assertEquals(7, PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot.getParentTree()).size());
                assertEquals(4, structureTreeRoot.getRoleMap().size());

                // check that destinations are fixed (only the two first point to the split doc)
                List<PDAnnotation> annotations = dstDoc.getPage(0).getAnnotations();
                assertEquals(5, annotations.size());
                PDAnnotationLink link1 = (PDAnnotationLink) annotations.get(0);
                PDAnnotationLink link2 = (PDAnnotationLink) annotations.get(1);
                PDAnnotationLink link3 = (PDAnnotationLink) annotations.get(2);
                PDAnnotationLink link4 = (PDAnnotationLink) annotations.get(3);
                PDAnnotationLink link5 = (PDAnnotationLink) annotations.get(4);
                PDPageDestination pd1 =
                        (PDPageDestination) ((PDActionGoTo) link1.getAction()).getDestination();
                PDPageDestination pd2 =
                        (PDPageDestination) ((PDActionGoTo) link2.getAction()).getDestination();
                PDPageDestination pd3 =
                        (PDPageDestination) ((PDActionGoTo) link3.getAction()).getDestination();
                PDPageDestination pd4 =
                        (PDPageDestination) ((PDActionGoTo) link4.getAction()).getDestination();
                PDPageDestination pd5 =
                        (PDPageDestination) ((PDActionGoTo) link5.getAction()).getDestination();
                PDPageTree pageTree = dstDoc.getPages();
                assertEquals(0, pageTree.indexOf(pd1.getPage()));
                assertEquals(1, pageTree.indexOf(pd2.getPage()));
                assertNull(pd3.getPage());
                assertNull(pd4.getPage());
                assertNull(pd5.getPage());
            }
        }
    }

    /**
     * PDFBOX-5929: Check that orphan annotations are removed from the structure tree if annotations
     * were removed from the pages (don't do that!).
     *
     * @throws IOException
     */
    @Test
    public void testSplitWithStructureTreeAndDestinationsAndRemovedAnnotations() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(SRCDIR,"PDFBOX-5762-722238.pdf")))
        {
            Splitter splitter = new Splitter();
            for (PDPage page : doc.getPages())
            {
                page.setAnnotations(Collections.emptyList());
            }
            splitter.setStartPage(1);
            splitter.setEndPage(2);
            splitter.setSplitAtPage(2);
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(1, splitResult.size());
            try (PDDocument dstDoc = splitResult.get(0))
            {
                assertEquals(2, dstDoc.getNumberOfPages());
                checkForPageOrphans(dstDoc);
            }
        }
    }

    /**
     * Check for the bug that happened in PDFBOX-5792, where a destination was outside a target
     * document and hit an NPE in the next call of Splitter.fixDestinations().
     *
     * @throws IOException
     */
    @Test
    public void testSinglePageSplit() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(SRCDIR, "PDFBOX-5792-240045.pdf")))
        {
            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(1);
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(6, splitResult.size());
            for (PDDocument dstDoc : splitResult)
            {
                assertEquals(1, dstDoc.getNumberOfPages());
                checkForPageOrphans(dstDoc);
                for (PDAnnotation ann : dstDoc.getPage(0).getAnnotations())
                {
                    PDAnnotationLink link = (PDAnnotationLink) ann;
                    PDActionGoTo action = (PDActionGoTo) link.getAction();
                    PDPageDestination destination = (PDPageDestination) action.getDestination();
                    assertNull(destination.getPage());
                }
            }
            PDStructureTreeRoot structureTreeRoot1 = splitResult.get(0).getDocumentCatalog().getStructureTreeRoot();
            assertEquals(6, PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot1.getParentTree()).size());
            assertEquals(3, structureTreeRoot1.getRoleMap().size());
            PDStructureTreeRoot structureTreeRoot2 = splitResult.get(1).getDocumentCatalog().getStructureTreeRoot();
            assertEquals(6, PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot2.getParentTree()).size());
            assertEquals(3, structureTreeRoot2.getRoleMap().size());
            PDStructureTreeRoot structureTreeRoot3 = splitResult.get(2).getDocumentCatalog().getStructureTreeRoot();
            assertEquals(6, PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot3.getParentTree()).size());
            assertEquals(4, structureTreeRoot3.getRoleMap().size());
            PDStructureTreeRoot structureTreeRoot4 = splitResult.get(3).getDocumentCatalog().getStructureTreeRoot();
            assertEquals(5, PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot4.getParentTree()).size());
            assertEquals(4, structureTreeRoot4.getRoleMap().size());
            PDStructureTreeRoot structureTreeRoot5 = splitResult.get(4).getDocumentCatalog().getStructureTreeRoot();
            assertEquals(1, PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot5.getParentTree()).size());
            assertEquals(6, structureTreeRoot5.getRoleMap().size());
            PDStructureTreeRoot structureTreeRoot6 = splitResult.get(5).getDocumentCatalog().getStructureTreeRoot();
            assertEquals(1, PDFMergerUtility.getNumberTreeAsMap(structureTreeRoot6.getParentTree()).size());
            assertEquals(7, structureTreeRoot6.getRoleMap().size());
            for (PDDocument dstDoc : splitResult)
            {
                dstDoc.close();
            }
        }
    }

    @Test
    public void testSplitWithPopupAnnotations() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(SRCDIR, "PDFBOX-5809-509329.pdf")))
        {
            Splitter splitter = new Splitter();
            splitter.setStartPage(3);
            splitter.setEndPage(3);
            splitter.setSplitAtPage(1);
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(1, splitResult.size());
            List<PDAnnotation> annotations;
            PDAnnotationText annotationText3;
            PDAnnotationPopup annotationPopup4;
            try (PDDocument dstDoc = splitResult.get(0))
            {
                checkForPageOrphans(dstDoc);
                assertEquals(1, dstDoc.getNumberOfPages());
                annotations = dstDoc.getPage(0).getAnnotations();
                assertEquals(5, annotations.size());
                annotationText3 = (PDAnnotationText) annotations.get(3);
                annotationPopup4 = (PDAnnotationPopup) annotations.get(4);
                assertEquals(annotationText3.getPopup(), annotationPopup4);
                assertEquals(annotationPopup4.getParent(), annotationText3);
                assertEquals(annotationText3.getPage(), dstDoc.getPage(0));
            }
            // Check that source document is ok
            annotations = doc.getPage(2).getAnnotations();
            assertEquals(5, annotations.size());
            annotationText3 = (PDAnnotationText) annotations.get(3);
            annotationPopup4 = (PDAnnotationPopup) annotations.get(4);
            assertEquals(annotationText3.getPopup(), annotationPopup4);
            assertEquals(annotationPopup4.getParent(), annotationText3);
            assertEquals(annotationText3.getPage(), doc.getPage(2));
        }
    }

    @Test
    public void testSplitWithBrokenDestination() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(SRCDIR, "PDFBOX-5811-362972.pdf")))
        {
            Splitter splitter = new Splitter();
            splitter.setStartPage(2);
            splitter.setEndPage(2);
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(1, splitResult.size());
            List<PDAnnotation> annotations;
            try (PDDocument dstDoc = splitResult.get(0))
            {
                checkForPageOrphans(dstDoc);
                assertEquals(1, dstDoc.getNumberOfPages());
                annotations = dstDoc.getPage(0).getAnnotations();
                assertEquals(1, annotations.size());
                PDAnnotationLink link = (PDAnnotationLink) annotations.get(0);
                assertNull(link.getDestination());
            }
            // Check source document
            annotations = doc.getPage(1).getAnnotations();
            assertEquals(1, annotations.size());
            PDAnnotationLink link = (PDAnnotationLink) annotations.get(0);
            assertThrows(IOException.class, link::getDestination);
        }
    }

    @Test
    public void testSplitWithNamedDestinations() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(SRCDIR, "PDFBOX-5840-410609.pdf")))
        {
            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(6);
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(1, splitResult.size());
            List<PDAnnotation> annotations;
            try (PDDocument dstDoc = splitResult.get(0))
            {
                checkForPageOrphans(dstDoc);
                assertEquals(6, dstDoc.getNumberOfPages());
                annotations = dstDoc.getPage(0).getAnnotations();
                assertEquals(5, annotations.size());
                PDAnnotationLink link1 = (PDAnnotationLink) annotations.get(0);
                PDAnnotationLink link2 = (PDAnnotationLink) annotations.get(1);
                PDAnnotationLink link3 = (PDAnnotationLink) annotations.get(2);
                PDAnnotationLink link4 = (PDAnnotationLink) annotations.get(3);
                PDAnnotationLink link5 = (PDAnnotationLink) annotations.get(4);
                PDPageDestination pd1 =
                        (PDPageDestination) ((PDActionGoTo) link1.getAction()).getDestination();
                PDPageDestination pd2 =
                        (PDPageDestination) ((PDActionGoTo) link2.getAction()).getDestination();
                PDPageDestination pd3 =
                        (PDPageDestination) ((PDActionGoTo) link3.getAction()).getDestination();
                PDPageDestination pd4 =
                        (PDPageDestination) ((PDActionGoTo) link4.getAction()).getDestination();
                PDPageDestination pd5 =
                        (PDPageDestination) ((PDActionGoTo) link5.getAction()).getDestination();
                PDPageTree pageTree = dstDoc.getPages();
                assertEquals(0, pageTree.indexOf(pd1.getPage()));
                assertEquals(1, pageTree.indexOf(pd2.getPage()));
                assertEquals(3, pageTree.indexOf(pd3.getPage()));
                assertEquals(3, pageTree.indexOf(pd4.getPage()));
                assertEquals(5, pageTree.indexOf(pd5.getPage()));

                assertNotNull(dstDoc.getDocumentCatalog().getMetadata());

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                dstDoc.save(baos);
                try (PDDocument reloadedDoc = Loader.loadPDF(baos.toByteArray()))
                {
                    assertNotNull(reloadedDoc.getDocumentCatalog().getMetadata());
                }
            }
            // Check that source document is unchanged
            annotations = doc.getPage(0).getAnnotations();
            assertEquals(5, annotations.size());
            PDAnnotationLink link = (PDAnnotationLink) annotations.get(0);
            assertTrue(((PDActionGoTo) link.getAction()).getDestination() instanceof PDNamedDestination);
        }
    }

    /**
     * PDFBOX-6009: This test verifies that the destination PDF has a /K tree. Before the change,
     * nodes with the "wrong" /Pg entries were deleted entirely and because this file has a /Pg
     * entry with page 1 at the top, the entire /K tree would be missing.
     *
     * @throws IOException
     */
    @Test
    public void testSplitWithPgEntryAtTheTop() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(TARGETPDFDIR, "PDFBOX-6009.pdf")))
        {
            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(1);
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(3, splitResult.size());
            for (PDDocument dstDoc : splitResult)
            {
                assertEquals(1, dstDoc.getNumberOfPages());
                checkWithNumberTree(dstDoc);
                checkForPageOrphans(dstDoc);
            }
            splitResult.stream().forEach(IOUtils::closeQuietly);
        }
    }

    /**
     * PDFBOX-6018: Test split a PDF with popup annotations that are not in the annotations list.
     * Verify that after splitting, they still link back to their markup annotation and these to the
     * page.
     *
     * @throws IOException
     */
    @Test
    public void testSplitWithOrphanPopupAnnotation() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(new File(SRCDIR, "PDFBOX-6018-099267-p9-OrphanPopups.pdf")))
        {
            Splitter splitter = new Splitter();
            List<PDDocument> splitResult = splitter.split(doc);
            assertEquals(1, splitResult.size());
            try (PDDocument dstDoc = splitResult.get(0))
            {
                assertEquals(1, dstDoc.getNumberOfPages());
                PDPage page = dstDoc.getPage(0);
                List<PDAnnotation> annotations = page.getAnnotations();
                assertEquals(2, annotations.size());
                PDAnnotationText ann0 = (PDAnnotationText) annotations.get(0);
                PDAnnotationText ann1 = (PDAnnotationText) annotations.get(1);
                assertEquals(page, ann0.getPage());
                assertEquals(page, ann1.getPage());
                assertEquals(ann0, ann0.getPopup().getParent());
                assertEquals(ann1, ann1.getPopup().getParent());
            }
        }
    }

    /**
     * PDFBOX-5939: merge a file with an outline that has itself as a parent without producing a
     * stack overflow.
     *
     * @throws IOException
     */
    @Test
    public void testOutlinesSelfParent() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        pdfMergerUtility.addSource(new File(TARGETPDFDIR, "PDFBOX-5939-google-docs-1.pdf"));
        pdfMergerUtility.addSource(new File(TARGETPDFDIR, "PDFBOX-5939-google-docs-1.pdf"));
        pdfMergerUtility.setDestinationFileName(TARGETTESTDIR + "PDFBOX-5939-google-docs-result.pdf");
        pdfMergerUtility.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());

        try (PDDocument mergedDoc = Loader
                .loadPDF(new File(TARGETTESTDIR, "PDFBOX-5939-google-docs-result.pdf")))
        {
            assertEquals(2, mergedDoc.getNumberOfPages());
        }
    }

    /**
     * PDFBOX-515 / PDFBOX-5950: test merging of two files where one file has a stream deep down in
     * the info dictionary (Info/ImPDF/Images/Kids/[0]). This test will pass only if the source file
     * isn't closed prematurely, or if deep cloning is applied.
     *
     * @throws IOException
     */
    @Test
    public void testPDFBox515() throws IOException
    {
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
        pdfMergerUtility.addSource(new File(TARGETPDFDIR, "ComSquare1.pdf"));
        pdfMergerUtility.addSource(new File(TARGETPDFDIR, "Ghostscript1.pdf"));
        pdfMergerUtility.setDestinationFileName(TARGETTESTDIR + "PDFBOX-515-result.pdf");
        pdfMergerUtility.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());

        try (PDDocument mergedDoc = Loader.loadPDF(new File(TARGETTESTDIR, "PDFBOX-515-result.pdf")))
        {
            assertEquals(2, mergedDoc.getNumberOfPages());
            COSDictionary imageDict = (COSDictionary) mergedDoc.getDocumentInformation().getCOSObject().
                    getCOSDictionary(COSName.getPDFName("ImPDF")).
                    getCOSDictionary(COSName.getPDFName("Images")).
                    getCOSArray(COSName.KIDS).getObject(0);
            PDImageXObject imageXObject = (PDImageXObject) PDImageXObject.createXObject(imageDict, new PDResources());
            Bitmap bim = imageXObject.getImage();
            assertEquals(909, bim.getWidth());
            assertEquals(233, bim.getHeight());
        }
    }
}
