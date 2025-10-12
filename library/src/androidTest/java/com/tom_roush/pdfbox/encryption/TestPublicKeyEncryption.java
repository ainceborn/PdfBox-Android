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
package com.tom_roush.pdfbox.encryption;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;

import javax.crypto.Cipher;

import com.tom_roush.pdfbox.Loader;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.IOUtils;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.io.RandomAccessReadBuffer;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission;
import com.tom_roush.pdfbox.pdmodel.encryption.PublicKeyProtectionPolicy;
import com.tom_roush.pdfbox.pdmodel.encryption.PublicKeyRecipient;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for public key encryption. These tests are not perfect - to be sure, encrypt a file by
 * using a certificate exported from your digital id in Adobe Reader, and then open that file with
 * Adobe Reader. Do this with every key length.
 *
 * @author Ben Litchfield
 */
@RunWith(Parameterized.class)
class TestPublicKeyEncryption
{
    private static final File TESTRESULTSDIR = new File("target/test-output/crypto");

    private AccessPermission permission1;
    private AccessPermission permission2;

    private PublicKeyRecipient recipient1;
    private PublicKeyRecipient recipient2;

    private String keyStore1;
    private String keyStore2;

    private String password1;
    private String password2;

    /**
     * Simple test document that gets encrypted by the test cases.
     */
    private PDDocument document;

    private String text;
    private String producer;

    public int keyLength;

    /**
     * Values for keyLength test parameter.
     *
     * @return
     */
    @Parameterized.Parameters(name = "keyLength={0}")
    public static Collection<Integer> keyLengths()
    {
        return Arrays.asList(40, 128, 256);
    }

    public TestPublicKeyEncryption(int keyLength) {
        this.keyLength = keyLength;
    }

    @BeforeClass
    public static void init() throws NoSuchAlgorithmException
    {
        if (Cipher.getMaxAllowedKeyLength("AES") != Integer.MAX_VALUE)
        {
            // we need strong encryption for these tests
            fail("JCE unlimited strength jurisdiction policy files are not installed");
        }
        TESTRESULTSDIR.mkdirs();
    }

    /**
     * {@inheritDoc}
     */
    @Before
    public void setUp() throws IOException, CertificateException, URISyntaxException
    {
        permission1 = new AccessPermission();
        permission1.setCanAssembleDocument(false);
        permission1.setCanExtractContent(false);
        permission1.setCanExtractForAccessibility(true);
        permission1.setCanFillInForm(false);
        permission1.setCanModify(false);
        permission1.setCanModifyAnnotations(false);
        permission1.setCanPrint(false);
        permission1.setCanPrintFaithful(false);

        permission2 = new AccessPermission();
        permission2.setCanAssembleDocument(false);
        permission2.setCanExtractContent(false);
        permission2.setCanExtractForAccessibility(true);
        permission2.setCanFillInForm(false);
        permission2.setCanModify(false);
        permission2.setCanModifyAnnotations(false);
        permission2.setCanPrint(true); // it is true now !
        permission2.setCanPrintFaithful(false);

        recipient1 = getRecipient("test1.der", permission1);
        recipient2 = getRecipient("test2.der", permission2);

        password1 = "test1";
        password2 = "test2";

        keyStore1 = "test1.pfx";
        keyStore2 = "test2.pfx";

        document = Loader.loadPDF(new File(this.getClass().getResource("test.pdf").toURI()));
        text = new PDFTextStripper().getText(document);
        producer = document.getDocumentInformation().getProducer();
        document.setVersion(1.7f);
    }

    /**
     * {@inheritDoc}
     */
    @After
    public void tearDown() throws IOException
    {
        document.close();
    }

    /**
     * Protect a document with certificate 1 and try to open it with
     * certificate 2 and catch the exception.
     *
     * @throws IOException If there is an unexpected error during the test.
     */

    @Test
    public void testProtectionError() throws IOException
    {
        PublicKeyProtectionPolicy policy = new PublicKeyProtectionPolicy();
        policy.addRecipient(recipient1);
        policy.setEncryptionKeyLength(keyLength);
        document.protect(policy);

        File file = save("testProtectionError");
        try (PDDocument encryptedDoc = reload(file, password2, getKeyStore(keyStore2)))
        {
            assertTrue(encryptedDoc.isEncrypted());
            fail("No exception when using an incorrect decryption key");
        }
        catch (IOException ex)
        {
            String msg = ex.getMessage();
            assertTrue("not the expected exception: " + msg,
                    msg.contains("serial-#: rid 2 vs. cert 3"));
        }
    }


    /**
     * Protect a document with a public certificate and try to open it
     * with the corresponding private certificate.
     *
     * @throws IOException If there is an unexpected error during the test.
     */
    @Test
    public void testProtection() throws IOException
    {
        PublicKeyProtectionPolicy policy = new PublicKeyProtectionPolicy();
        policy.addRecipient(recipient1);
        policy.setEncryptionKeyLength(keyLength);
        document.protect(policy);

        File file = save("testProtection");
        try (PDDocument encryptedDoc = reload(file, password1, getKeyStore(keyStore1)))
        {
            assertTrue(encryptedDoc.isEncrypted());

            AccessPermission permission = encryptedDoc.getCurrentAccessPermission();
            assertFalse(permission.canAssembleDocument());
            assertFalse(permission.canExtractContent());
            assertTrue(permission.canExtractForAccessibility());
            assertFalse(permission.canFillInForm());
            assertFalse(permission.canModify());
            assertFalse(permission.canModifyAnnotations());
            assertFalse(permission.canPrint());
            assertFalse(permission.canPrintFaithful());
        }
    }


    /**
     * Protect the document for 2 recipients and try to open it.
     *
     * @throws IOException If there is an error during the test.
     */
    @Test
    public void testMultipleRecipients() throws IOException
    {
        PublicKeyProtectionPolicy policy = new PublicKeyProtectionPolicy();
        policy.addRecipient(recipient1);
        policy.addRecipient(recipient2);
        policy.setEncryptionKeyLength(keyLength);
        document.protect(policy);

        // open first time
        File file = save("testMultipleRecipients");
        try (PDDocument encryptedDoc1 = reload(file, password1, getKeyStore(keyStore1)))
        {
            AccessPermission permission = encryptedDoc1.getCurrentAccessPermission();
            assertFalse(permission.canAssembleDocument());
            assertFalse(permission.canExtractContent());
            assertTrue(permission.canExtractForAccessibility());
            assertFalse(permission.canFillInForm());
            assertFalse(permission.canModify());
            assertFalse(permission.canModifyAnnotations());
            assertFalse(permission.canPrint());
            assertFalse(permission.canPrintFaithful());
        }

        // open second time
        try (PDDocument encryptedDoc2 = reload(file, password2, getKeyStore(keyStore2)))
        {
            AccessPermission permission = encryptedDoc2.getCurrentAccessPermission();
            assertFalse(permission.canAssembleDocument());
            assertFalse(permission.canExtractContent());
            assertTrue(permission.canExtractForAccessibility());
            assertFalse(permission.canFillInForm());
            assertFalse(permission.canModify());
            assertFalse(permission.canModifyAnnotations());
            assertTrue(permission.canPrint());
            assertFalse(permission.canPrintFaithful());
        }
    }

    /**
     * Reloads the given document from a file and check some contents.
     *
     * @param file input file
     * @param decryptionPassword password to be used to decrypt the doc
     * @param keyStore password to be used to decrypt the doc
     * @return reloaded document
     * @throws Exception if
     */
    private PDDocument reload(File file, String decryptionPassword, InputStream keyStore)
            throws IOException
    {
        PDDocument doc2 = Loader.loadPDF(file, decryptionPassword,
                keyStore, null, IOUtils.createMemoryOnlyStreamCache());
        assertEquals(text, new PDFTextStripper().getText(doc2),
                "Extracted text is different");
        assertEquals(producer, doc2.getDocumentInformation().getProducer(),
                "Producer is different");
        return doc2;
    }

    /**
     * Returns a recipient specification with the given access permissions
     * and an X.509 certificate read from the given classpath resource.
     *
     * @param certificate X.509 certificate resource, relative to this class
     * @param permission access permissions
     * @return recipient specification
     * @throws CertificateException if the certificate could not be read
     * @throws IOException
     */
    private static PublicKeyRecipient getRecipient(String certificate, AccessPermission permission)
            throws IOException, CertificateException
    {
        try (InputStream input = TestPublicKeyEncryption.class.getResourceAsStream(certificate))
        {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            PublicKeyRecipient recipient = new PublicKeyRecipient();
            recipient.setPermission(permission);
            recipient.setX509((X509Certificate) factory.generateCertificate(input));
            return recipient;
        }
    }

    private InputStream getKeyStore(String name)
    {
        return TestPublicKeyEncryption.class.getResourceAsStream(name);
    }

    private File save(String name) throws IOException
    {
        File file = new File(TESTRESULTSDIR, name + "-" + keyLength + "bit.pdf");
        document.save(file);
        return file;
    }

    /**
     * PDFBOX-4421: Read a file encrypted with AES128 but not with PDFBox, and with missing /Length
     * entry.
     *
     * @throws IOException
     */
    @Test
    public void testReadPubkeyEncryptedAES128() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(
                RandomAccessReadBuffer.createBufferFromStream(
                        TestPublicKeyEncryption.class.getResourceAsStream("AESkeylength128.pdf")),
                "w!z%C*F-JaNdRgUk",
                TestPublicKeyEncryption.class.getResourceAsStream("PDFBOX-4421-keystore.pfx"),
                "testnutzer"))
        {
            assertEquals("PublicKeySecurityHandler",
                    doc.getEncryption().getSecurityHandler().getClass().getSimpleName());
            assertEquals(128, doc.getEncryption().getSecurityHandler().getKeyLength());
            PDFTextStripper stripper = new PDFTextStripper();
            assertEquals("Key length: 128", stripper.getText(doc).trim());
        }
    }

    /**
     * PDFBOX-4421: Read a file encrypted with AES128 but not with PDFBox, and with missing /Length
     * entry.
     *
     * @throws IOException
     */
    @Test
    public void testReadPubkeyEncryptedAES256() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(
                RandomAccessReadBuffer.createBufferFromStream(
                        TestPublicKeyEncryption.class.getResourceAsStream("AESkeylength256.pdf")),
                "w!z%C*F-JaNdRgUk",
                TestPublicKeyEncryption.class.getResourceAsStream("PDFBOX-4421-keystore.pfx"),
                "testnutzer"))
        {
            assertEquals("PublicKeySecurityHandler",
                    doc.getEncryption().getSecurityHandler().getClass().getSimpleName());
            assertEquals(256, doc.getEncryption().getSecurityHandler().getKeyLength());
            PDFTextStripper stripper = new PDFTextStripper();
            assertEquals("Key length: 256", stripper.getText(doc).trim());
        }
    }

    /**
     * PDFBOX-5249: Read a file encrypted with AES128 but not with PDFBox, and with exposed
     * Metadata.
     *
     * @throws IOException
     */
    @Test
    public void testReadPubkeyEncryptedAES128withMetadataExposed() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(
                RandomAccessReadBuffer.createBufferFromStream(
                        TestPublicKeyEncryption.class.getResourceAsStream("AES128ExposedMeta.pdf")), //
                "", TestPublicKeyEncryption.class.getResourceAsStream("PDFBOX-5249.p12"), //
                "test", IOUtils.createMemoryOnlyStreamCache()))
        {
            assertEquals("PublicKeySecurityHandler",
                    doc.getEncryption().getSecurityHandler().getClass().getSimpleName());
            assertEquals(128, doc.getEncryption().getSecurityHandler().getKeyLength());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            assertEquals("AES key length: 128\nwith exposed Metadata", stripper.getText(doc).trim());
        }
    }

    /**
     * PDFBOX-5249: Read a file encrypted with AES128 but not with PDFBox, and with exposed
     * Metadata.
     *
     * @throws IOException
     */
    @Test
    public void testReadPubkeyEncryptedAES256withMetadataExposed() throws IOException
    {
        try (PDDocument doc = Loader.loadPDF(
                RandomAccessReadBuffer.createBufferFromStream(
                        TestPublicKeyEncryption.class.getResourceAsStream("AES256ExposedMeta.pdf")), //
                "", TestPublicKeyEncryption.class.getResourceAsStream("PDFBOX-5249.p12"), //
                "test", IOUtils.createMemoryOnlyStreamCache()))
        {
            assertEquals("PublicKeySecurityHandler",
                    doc.getEncryption().getSecurityHandler().getClass().getSimpleName());
            assertEquals(256, doc.getEncryption().getSecurityHandler().getKeyLength());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            assertEquals("AES key length: 256 \nwith exposed Metadata", stripper.getText(doc).trim());
        }
    }
}
