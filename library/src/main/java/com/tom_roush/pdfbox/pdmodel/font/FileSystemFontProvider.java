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

import android.os.Build;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;

import com.tom_roush.fontbox.FontBoxFont;
import com.tom_roush.fontbox.ttf.FontHeaders;
import com.tom_roush.fontbox.ttf.OS2WindowsMetricsTable;
import com.tom_roush.fontbox.ttf.OTFParser;
import com.tom_roush.fontbox.ttf.OpenTypeFont;
import com.tom_roush.fontbox.ttf.TTFParser;
import com.tom_roush.fontbox.ttf.TrueTypeCollection;
import com.tom_roush.fontbox.ttf.TrueTypeFont;
import com.tom_roush.fontbox.type1.Type1Font;
import com.tom_roush.fontbox.util.autodetect.FontFileFinder;
import com.tom_roush.pdfbox.io.IOUtils;
import com.tom_roush.pdfbox.io.RandomAccessReadBufferedFile;
import com.tom_roush.pdfbox.util.TTFFonts;

/**
 * A FontProvider which searches for fonts on the local filesystem.
 *
 * @author John Hewson
 */
final class FileSystemFontProvider extends FontProvider
{

    private final List<FSFontInfo> fontInfoList = new ArrayList<>();
    private final FontCache cache;

    private static class FSFontInfo extends FontInfo
    {
        private final String postScriptName;
        private final FontFormat format;
        private final CIDSystemInfo cidSystemInfo;
        private final int usWeightClass;
        private final int sFamilyClass;
        private final int ulCodePageRange1;
        private final int ulCodePageRange2;
        private final int macStyle;
        private final PDPanoseClassification panose;
        private final File file;
        private final FileSystemFontProvider parent;
        private final String hash;
        private final long lastModified;

        private FSFontInfo(File file, FontFormat format, String postScriptName,
                           CIDSystemInfo cidSystemInfo, int usWeightClass, int sFamilyClass,
                           int ulCodePageRange1, int ulCodePageRange2, int macStyle, byte[] panose,
                           FileSystemFontProvider parent, String hash, long lastModified)
        {
            this.file = file;
            this.format = format;
            this.postScriptName = postScriptName;
            this.cidSystemInfo = cidSystemInfo;
            this.usWeightClass = usWeightClass;
            this.sFamilyClass = sFamilyClass;
            this.ulCodePageRange1 = ulCodePageRange1;
            this.ulCodePageRange2 = ulCodePageRange2;
            this.macStyle = macStyle;
            this.panose = panose != null && panose.length >= PDPanoseClassification.LENGTH ?
                    new PDPanoseClassification(panose) : null;
            this.parent = parent;
            this.hash = hash;
            this.lastModified = lastModified;
        }

        @Override
        public String getPostScriptName()
        {
            return postScriptName;
        }

        @Override
        public FontFormat getFormat()
        {
            return format;
        }

        @Override
        public CIDSystemInfo getCIDSystemInfo()
        {
            return cidSystemInfo;
        }

        /**
         * {@inheritDoc}
         * <p>
         * The method returns null if there was an error opening the font.
         *
         */
        @Override
        public synchronized FontBoxFont getFont()
        {
            // synchronized to avoid race condition on cache access,
            // which could result in an unreferenced but open font
            FontBoxFont cached = parent.cache.getFont(this);
            if (cached != null)
            {
                return cached;
            }
            else
            {
                FontBoxFont font;
                switch (format)
                {
                    case PFB: font = getType1Font(postScriptName, file); break;
                    case TTF: font = getTrueTypeFont(postScriptName, file); break;
                    case OTF: font = getOTFFont(postScriptName, file); break;
                    default: throw new RuntimeException("can't happen");
                }
                if (font != null)
                {
                    parent.cache.addFont(this, font);
                }
                return font;
            }
        }

        @Override
        public int getFamilyClass()
        {
            return sFamilyClass;
        }

        @Override
        public int getWeightClass()
        {
            return usWeightClass;
        }

        @Override
        public int getCodePageRange1()
        {
            return ulCodePageRange1;
        }

        @Override
        public int getCodePageRange2()
        {
            return ulCodePageRange2;
        }

        @Override
        public int getMacStyle()
        {
            return macStyle;
        }

        @Override
        public PDPanoseClassification getPanose()
        {
            return panose;
        }

        @Override
        public String toString()
        {
            return super.toString() + " " + file + " " + hash + " " + lastModified;
        }

        private TrueTypeFont getTrueTypeFont(String postScriptName, File file)
        {
            try
            {
                TrueTypeFont ttf = readTrueTypeFont(postScriptName, file);
                Log.d("PdfBox-Android", "Loaded " + postScriptName + " from " + file);
                return ttf;
            }
            catch (IOException e)
            {
                Log.w("PdfBox-Android", "Could not load font file: " + file, e);
            }
            return null;
        }

        private TrueTypeFont readTrueTypeFont(String postScriptName, File file) throws IOException
        {
            if (file.getName().toLowerCase().endsWith(".ttc"))
            {
                @SuppressWarnings("squid:S2095")
                // ttc not closed here because it is needed later when ttf is accessed,
                // e.g. rendering PDF with non-embedded font which is in ttc file in our font directory
                TrueTypeCollection ttc = new TrueTypeCollection(file);
                TrueTypeFont ttf;
                try
                {
                    ttf = ttc.getFontByName(postScriptName);
                }
                catch (IOException ex)
                {
                    ttc.close();
                    throw ex;
                }
                if (ttf == null)
                {
                    ttc.close();
                    throw new IOException("Font " + postScriptName + " not found in " + file);
                }
                return ttf;
            }
            else
            {
                TTFParser ttfParser = new TTFParser(false);
                return ttfParser.parse(new RandomAccessReadBufferedFile(file));
            }
        }

        private OpenTypeFont getOTFFont(String postScriptName, File file)
        {
            try
            {
                if (file.getName().toLowerCase().endsWith(".ttc"))
                {
                    @SuppressWarnings("squid:S2095")
                    // ttc not closed here because it is needed later when ttf is accessed,
                    // e.g. rendering PDF with non-embedded font which is in ttc file in our font directory
                    TrueTypeCollection ttc = new TrueTypeCollection(file);
                    TrueTypeFont ttf;
                    try
                    {
                        ttf = ttc.getFontByName(postScriptName);
                    }
                    catch (IOException ex)
                    {
                        Log.e("PdfBox-Android", ex.getMessage(), ex);
                        ttc.close();
                        return null;
                    }
                    if (ttf == null)
                    {
                        ttc.close();
                        throw new IOException("Font " + postScriptName + " not found in " + file);
                    }
                    return (OpenTypeFont) ttf;
                }

                OTFParser parser = new OTFParser(false);
                OpenTypeFont otf = parser.parse(new RandomAccessReadBufferedFile(file));

                Log.d("PdfBox-Android", "Loaded " + postScriptName + " from " + file);
                return otf;
            }
            catch (IOException e)
            {
                Log.w("PdfBox-Android", "Could not load font file: " + file, e);
            }
            return null;
        }

        private Type1Font getType1Font(String postScriptName, File file)
        {
            try (InputStream input = new FileInputStream(file))
            {
                Type1Font type1 = Type1Font.createWithPFB(input);
                Log.d("PdfBox-Android", "Loaded " + postScriptName + " from " + file);
                return type1;
            }
            catch (IOException e)
            {
                Log.w("PdfBox-Android", "Could not load font file: " + file, e);
            }
            return null;
        }
    }

    private FSFontInfo createFSIgnored(File file, FontFormat format, String postScriptName)
    {
        String hash;
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hash = computeHash(Files.newInputStream(file.toPath()));
            } else {
                hash = computeHash(IOUtils.newInputStream(file));
            }
        }
        catch (IOException ex)
        {
            hash = "";
        }
        return new FSFontInfo(file, format, postScriptName, null, 0, 0, 0, 0, 0, null, null, hash, file.lastModified());
    }

    /**
     * Constructor.
     */
    FileSystemFontProvider(FontCache cache)
    {
        this.cache = cache;
        try
        {
            Log.d("PdfBox-Android", "Will search the local system for fonts");

            // scan the local system for font files
            FontFileFinder fontFileFinder = new FontFileFinder();
            List<URI> fonts = fontFileFinder.find();
            List<File> files = new ArrayList<>(fonts.size());
            for (URI font : fonts)
            {
                files.add(new File(font));
            }

            Log.d("PdfBox-Android", "Found " + files.size() + " fonts on the local system");

            if (!files.isEmpty())
            {
                // load cached FontInfo objects
                Pair<List<File>, List<FSFontInfo>> result = loadDiskCacheFromDir(files);
                List<FSFontInfo> cachedInfos;
                cachedInfos = Objects.requireNonNullElse(result.second, Collections.emptyList());

                List<File> toScan = result.first;

                if (!cachedInfos.isEmpty())
                {
                    fontInfoList.addAll(cachedInfos);
                }

                scanFonts(files);

                Set<String> existingMap = new HashSet<>();

                for (FSFontInfo info : fontInfoList) {
                    File f = info.file;
                    existingMap.add(f.getAbsolutePath() + "|" + f.length());
                }

                for (FSFontInfo info : cachedInfos) {
                    File f = info.file;
                    existingMap.add(f.getAbsolutePath() + "|" + f.length());
                }

                var filesToScan = new ArrayList<File>();

                for (File info :  TTFFonts.fontList) {
                    String key = info.getAbsolutePath() + "|" + info.length();

                    if (!existingMap.contains(key)) {
                        filesToScan.add(info);
                        existingMap.add(key);
                    }
                }

                for (File info :  toScan) {
                    String key = info.getAbsolutePath() + "|" + info.length();

                    if (!existingMap.contains(key)) {
                        filesToScan.add(info);
                        existingMap.add(key);
                    }
                }

                scanFonts(filesToScan);

                saveDiskCache();
            }
        }
        catch (AccessControlException e)
        {
            Log.e("PdfBox-Android", "Error accessing the file system", e);
        }
    }

    private void scanFonts(List<File> files)
    {
        // to force a specific font for debug, add code like this here:
        // files = Collections.singletonList(new File("font filename"))

        for (File file : files)
        {
            try
            {
                String filePath = file.getPath().toLowerCase();
                if (filePath.endsWith(".ttf") || filePath.endsWith(".otf"))
                {
                    addTrueTypeFont(file);
                }
                else if (filePath.endsWith(".ttc") || filePath.endsWith(".otc"))
                {
                    addTrueTypeCollection(file);
                }
                else if (filePath.endsWith(".pfb"))
                {
                    addType1Font(file);
                }
            }
            catch (Throwable e)
            {
                Log.w("PdfBox-Android", "Error parsing font " + file.getPath(), e);
            }
        }
    }

    private File getCacheDir()
    {
        String path = TTFFonts.cachePath;
        if (isBadPath(path))
        {
            path = System.getProperty("user.home");
            if (isBadPath(path))
            {
                path = System.getProperty("java.io.tmpdir");
            }
        }
        var cacheDir = new File(path, "cache");
        if(!cacheDir.exists()){
            cacheDir.mkdir();
        }
        return cacheDir;
    }

    private static boolean isBadPath(String path)
    {
        return path == null || !new File(path).isDirectory() || !new File(path).canWrite();
    }

    /**
     * Saves the font metadata cache to disk.
     */
    private void saveDiskCache()
    {
        try
        {
            for (FSFontInfo fontInfo : fontInfoList) {
                File file = getFontCacheFile(fontInfo);
                try (BufferedWriter writer = IOUtils.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    writeFontInfo(writer, fontInfo);
                }
                catch (IOException e)
                {
                    Log.w("PdfBox-Android", "Could not write to font cache", e);
                    Log.w("PdfBox-Android", "Installed fonts information will have to be reloaded for each start");
                    Log.w("PdfBox-Android", "You can assign a directory to the 'pdfbox.fontcache' property");
                }
            }
        }
        catch (IOException e)
        {
            Log.w("PdfBox-Android", "Could not create file for font", e);
        }
        catch (SecurityException e)
        {
            Log.d("PdfBox-Android", "Couldn't create writer for font cache file", e);
        }
    }

    private File getFontCacheFile(FSFontInfo fontInfo) throws IOException {
        File cacheDir = getCacheDir();

        String safeName = fontInfo.postScriptName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".txt";

        return new File(cacheDir, safeName);
    }

    private void writeFontInfo(BufferedWriter writer, FSFontInfo fontInfo) throws IOException
    {
        writer.write(fontInfo.postScriptName.trim());
        writer.write("|");
        writer.write(fontInfo.format.toString());
        writer.write("|");
        if (fontInfo.cidSystemInfo != null)
        {
            writer.write(fontInfo.cidSystemInfo.getRegistry() + '-' +
                    fontInfo.cidSystemInfo.getOrdering() + '-' +
                    fontInfo.cidSystemInfo.getSupplement());
        }
        writer.write("|");
        if (fontInfo.usWeightClass > -1)
        {
            writer.write(Integer.toHexString(fontInfo.usWeightClass));
        }
        writer.write("|");
        if (fontInfo.sFamilyClass > -1)
        {
            writer.write(Integer.toHexString(fontInfo.sFamilyClass));
        }
        writer.write("|");
        writer.write(Integer.toHexString(fontInfo.ulCodePageRange1));
        writer.write("|");
        writer.write(Integer.toHexString(fontInfo.ulCodePageRange2));
        writer.write("|");
        if (fontInfo.macStyle > -1)
        {
            writer.write(Integer.toHexString(fontInfo.macStyle));
        }
        writer.write("|");
        if (fontInfo.panose != null)
        {
            byte[] bytes = fontInfo.panose.getBytes();
            for (int i = 0; i < 10; i ++)
            {
                String str = Integer.toHexString(bytes[i]);
                if (str.length() == 1)
                {
                    writer.write('0');
                }
                writer.write(str);
            }
        }
        writer.write("|");
        writer.write(fontInfo.file.getAbsolutePath());
        writer.write("|");
        writer.write(fontInfo.hash);
        writer.write("|");
        writer.write(Long.toString(fontInfo.file.lastModified()));
        writer.newLine();
    }

    /**
     * Loads the font metadata cache from disk.
     */
    private Pair<List<File>, List<FSFontInfo>> loadDiskCacheFromDir(List<File> files) {
        Map<String, File> pending = new HashMap<>(files.size());

        for (File file : files) {
            pending.put(file.getAbsolutePath(), file);
        }

        List<FSFontInfo> results = new ArrayList<>();

        File cacheDir;
        cacheDir = getCacheDir();

        if (!cacheDir.exists() || !cacheDir.isDirectory()) {
            Log.i("PdfBox-Android", "Cache directory does not exist, rebuilding cache");
            return new Pair<>(Collections.emptyList(),Collections.emptyList());
        }

        File[] cacheFiles = cacheDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (cacheFiles == null || cacheFiles.length == 0) {
            Log.i("PdfBox-Android", "No font cache files found, rebuilding cache");
            return new Pair<>(Collections.emptyList(),Collections.emptyList());
        }

        for (File diskCacheFile : cacheFiles) {
            try (BufferedReader reader = IOUtils.newBufferedReaderCompat(diskCacheFile, StandardCharsets.UTF_8)) {
                String line;
                File lastFile = null;
                String lastHash = null;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|", 12);
                    if (parts.length < 10) {
                        Log.w("PdfBox-Android", "Incorrect line '{" + line + "}' in font cache file '{" + diskCacheFile.getName() +"}' is skipped");
                        continue;
                    }

                    String postScriptName = parts[0];
                    FontFormat format = FontFormat.valueOf(parts[1]);
                    CIDSystemInfo cidSystemInfo = null;
                    if (!parts[2].isEmpty()) {
                        String[] ros = parts[2].split("-");
                        cidSystemInfo = new CIDSystemInfo(ros[0], ros[1], Integer.parseInt(ros[2]));
                    }
                    int usWeightClass = !parts[3].isEmpty() ? (int)Long.parseLong(parts[3], 16) : -1;
                    int sFamilyClass = !parts[4].isEmpty() ? (int)Long.parseLong(parts[4], 16) : -1;
                    int ulCodePageRange1 = (int)Long.parseLong(parts[5], 16);
                    int ulCodePageRange2 = (int)Long.parseLong(parts[6], 16);
                    int macStyle = !parts[7].isEmpty() ? (int)Long.parseLong(parts[7], 16) : -1;
                    byte[] panose = null;
                    if (!parts[8].isEmpty()) {
                        panose = new byte[10];
                        for (int i = 0; i < 10; i++) {
                            panose[i] = (byte)Integer.parseInt(parts[8].substring(i*2, i*2+2), 16);
                        }
                    }
                    File fontFile = new File(parts[9]);
                    String hash = "";
                    long lastModified = 0;
                    if (parts.length >= 12 && !parts[10].isEmpty() && !parts[11].isEmpty()) {
                        hash = parts[10];
                        lastModified = Long.parseLong(parts[11]);
                    }

                    if (!fontFile.exists()) {
                        Log.d("PdfBox-Android", "Font file not found, skipped: " + fontFile.getAbsolutePath());
                        continue;
                    }

                    boolean keep = fontFile.lastModified() == lastModified;
                    if (!keep) {
                        String newHash;
                        if (hash.equals(lastHash) && fontFile.equals(lastFile)) {
                            newHash = lastHash;
                        } else {
                            try {
                                newHash = computeHash(Files.newInputStream(fontFile.toPath()));
                                lastFile = fontFile;
                                lastHash = newHash;
                            } catch (IOException ex) {
                                Log.d("PdfBox-Android", "Font file not found, skipped: " + fontFile.getAbsolutePath(), ex);
                                newHash = "<err>";
                            }
                        }
                        if (hash.equals(newHash)) {
                            keep = true;
                            lastModified = fontFile.lastModified();
                        }
                    }

                    if (keep) {
                        FSFontInfo info = new FSFontInfo(fontFile, format, postScriptName,
                                cidSystemInfo, usWeightClass, sFamilyClass,
                                ulCodePageRange1, ulCodePageRange2, macStyle, panose,
                                this, hash, lastModified);
                        results.add(info);
                        pending.remove(fontFile.getAbsolutePath());
                    }
                }
            } catch (IOException e) {
                Log.w("PdfBox-Android", "Error reading cache file, will rebuild cache: " +diskCacheFile.getAbsolutePath(), e);
                return new Pair<>(new ArrayList<>(pending.values()), results);
            }
        }

        if (!pending.isEmpty()) {
            Log.w("PdfBox-Android", " new font files found, font cache will be re-built " + pending.size());
            return new Pair<>(new ArrayList<>(pending.values()), results);
        }

        return new Pair<>(new ArrayList<>(pending.values()), results);
    }

    private FSFontInfo createFontInfo(File fontFile) {
        if (fontFile == null || !fontFile.exists()) {
            Log.w("PdfBox-Android", "Font file " + fontFile + " does not exist");
            return null;
        }

        String fileName = fontFile.getName().toLowerCase();
        String name = fontFile.getName();
        FontFormat format;

        if (fileName.endsWith(".ttf")) {
            format = FontFormat.TTF;
        } else if (fileName.endsWith(".otf")) {
            format = FontFormat.OTF;
        } else if (fileName.endsWith(".pfb")) {
            format = FontFormat.PFB;
        } else {
            Log.w("PdfBox-Android", "Unsupported font format: " + fontFile.getAbsolutePath());
            return null;
        }
        String postScriptName = name;

        try {
            if (format == FontFormat.TTF || format == FontFormat.OTF) {
                InputStream is = new  FileInputStream(fontFile);
                if (format == FontFormat.TTF) {
                    com.tom_roush.fontbox.ttf.TTFParser parser = new com.tom_roush.fontbox.ttf.TTFParser();
                    com.tom_roush.fontbox.ttf.TrueTypeFont ttf = parser.parse(new RandomAccessReadBufferedFile(fontFile));
                    postScriptName = ttf.getName();
                    ttf.close();
                } else {
                    com.tom_roush.fontbox.ttf.OTFParser parser = new com.tom_roush.fontbox.ttf.OTFParser();
                    com.tom_roush.fontbox.ttf.OpenTypeFont otf = parser.parse(new RandomAccessReadBufferedFile(fontFile));
                    postScriptName = otf.getName();
                    otf.close();
                }
                is.close();
            }
        } catch (IOException e) {
            Log.w("PdfBox-Android", "Failed to parse font file: " + fontFile, e);
        }

        var hash = "";
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hash = computeHash(Files.newInputStream(fontFile.toPath()));
            } else {
                hash = computeHash(IOUtils.newInputStream(fontFile));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } ;

        return new FSFontInfo(fontFile, format, postScriptName, null, -1, -1, 0, 0, -1, null, this, hash, fontFile.lastModified());
    }

    /**
     * Adds a TTC or OTC to the file cache. To reduce memory, the parsed font is not cached.
     */
    private void addTrueTypeCollection(final File ttcFile)
    {
        try
        {
            final String hash;
            try
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    hash = computeHash(Files.newInputStream(ttcFile.toPath()));
                } else {
                    hash = computeHash(IOUtils.newInputStream(ttcFile));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            TrueTypeCollection.processAllFontHeaders(ttcFile,
                    fontHeaders -> addTrueTypeFontImpl(fontHeaders, ttcFile, hash));
        }
        catch (IOException e)
        {
            Log.w("PdfBox-Android",  "Could not load font file: " + ttcFile, e);
            fontInfoList.add(createFSIgnored(ttcFile, FontFormat.TTF, "*skipexception*"));
        }
    }

    /**
     * Adds an OTF or TTF font to the file cache. To reduce memory, the parsed font is not cached.
     */
    private void addTrueTypeFont(File ttfFile)
    {
        FontFormat fontFormat = null;
        try
        {
            TTFParser parser;
            if (ttfFile.getPath().toLowerCase().endsWith(".otf"))
            {
                fontFormat = FontFormat.OTF;
                parser = new OTFParser(false);
            }
            else
            {
                fontFormat = FontFormat.TTF;
                parser = new TTFParser(false);
            }
            FontHeaders fontHeaders = parser.parseTableHeaders(new RandomAccessReadBufferedFile(ttfFile));

            String hash;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hash = computeHash(Files.newInputStream(ttfFile.toPath()));
            } else {
                hash =computeHash(IOUtils.newInputStream(ttfFile));
            }

            addTrueTypeFontImpl(fontHeaders, ttfFile, hash);
        }
        catch (IOException e)
        {
            Log.w("PdfBox-Android",  "Could not load font file: "+ ttfFile, e);
            fontInfoList.add(createFSIgnored(ttfFile, fontFormat, "*skipexception*"));
        }
    }

    /**
     * Adds an OTF or TTF font to the file cache. To reduce memory, the parsed font is not cached.
     */
    private void addTrueTypeFontImpl(FontHeaders fontHeaders, File file, String hash)
    {
        final String error = fontHeaders.getError();
        if (error == null)
        {
            // read PostScript name, if any
            final String name = fontHeaders.getName();
            if (name != null && name.contains("|"))
            {
                fontInfoList.add(createFSIgnored(file, FontFormat.TTF, "*skippipeinname*"));
                Log.w("PdfBox-Android", "Skipping font with '|' in name {"+ name + "} in file {"+ file +"}");
            }
            else if (name != null)
            {
                // ignore bitmap fonts
                Integer macStyle = fontHeaders.getHeaderMacStyle();
                if (macStyle == null)
                {
                    fontInfoList.add(createFSIgnored(file, FontFormat.TTF, name));
                    return;
                }

                int sFamilyClass = -1;
                int usWeightClass = -1;
                int ulCodePageRange1 = 0;
                int ulCodePageRange2 = 0;
                byte[] panose = null;
                OS2WindowsMetricsTable os2WindowsMetricsTable = fontHeaders.getOS2Windows();
                // Apple's AAT fonts don't have an OS/2 table
                if (os2WindowsMetricsTable != null)
                {
                    sFamilyClass = os2WindowsMetricsTable.getFamilyClass();
                    usWeightClass = os2WindowsMetricsTable.getWeightClass();
                    ulCodePageRange1 = (int) os2WindowsMetricsTable.getCodePageRange1();
                    ulCodePageRange2 = (int) os2WindowsMetricsTable.getCodePageRange2();
                    panose = os2WindowsMetricsTable.getPanose();
                }

                FontFormat format;
                CIDSystemInfo ros = null;
                if (fontHeaders.isOpenTypePostScript())
                {
                    format = FontFormat.OTF;
                    String registry = fontHeaders.getOtfRegistry();
                    String ordering = fontHeaders.getOtfOrdering();
                    if (registry != null || ordering != null)
                    {
                        ros = new CIDSystemInfo(registry, ordering, fontHeaders.getOtfSupplement());
                    }
                }
                else
                {
                    byte[] bytes = fontHeaders.getNonOtfTableGCID142();
                    if (bytes != null)
                    {
                        // Apple's AAT fonts have a "gcid" table with CID info
                        String reg = new String(bytes, 10, 64, StandardCharsets.US_ASCII);
                        String registryName = reg.substring(0, reg.indexOf('\0'));
                        String ord = new String(bytes, 76, 64, StandardCharsets.US_ASCII);
                        String orderName = ord.substring(0, ord.indexOf('\0'));
                        int supplementVersion = bytes[140] << 8 & (bytes[141] & 0xFF);
                        ros = new CIDSystemInfo(registryName, orderName, supplementVersion);
                    }
                    format = FontFormat.TTF;
                }
                fontInfoList.add(new FSFontInfo(file, format, name, ros,
                        usWeightClass, sFamilyClass, ulCodePageRange1, ulCodePageRange2,
                        macStyle, panose, this, hash, file.lastModified()));
            }
            else
            {
                fontInfoList.add(createFSIgnored(file, FontFormat.TTF, "*skipnoname*"));
                Log.w("PdfBox-Android", "Missing 'name' entry for PostScript name in font " + file);
            }
        }
        else
        {
            fontInfoList.add(createFSIgnored(file, FontFormat.TTF, "*skipexception*"));
            Log.w("PdfBox-Android", "Could not load font file '{" + file + "}': {"+ error +"}");
        }
    }

    /**
     * Adds a Type 1 font to the file cache. To reduce memory, the parsed font is not cached.
     */
    private void addType1Font(File pfbFile)
    {
        try (InputStream input = new FileInputStream(pfbFile))
        {
            Type1Font type1 = Type1Font.createWithPFB(input);
            if (type1.getName() == null)
            {
                fontInfoList.add(createFSIgnored(pfbFile, FontFormat.PFB, "*skipnoname*"));
                Log.w("PdfBox-Android", "Missing 'name' entry for PostScript name in font " + pfbFile);
                return;
            }
            if (type1.getName().contains("|"))
            {
                fontInfoList.add(createFSIgnored(pfbFile, FontFormat.PFB, "*skippipeinname*"));
                Log.w("PdfBox-Android", "Skipping font with '|' in name " + type1.getName() + " in file " + pfbFile);;
                return;
            }
            String hash = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hash = computeHash(Files.newInputStream(pfbFile.toPath()));
            } else {
                hash = computeHash(IOUtils.newInputStream(pfbFile));
            }
            fontInfoList.add(new FSFontInfo(pfbFile, FontFormat.PFB, type1.getName(),
                    null, -1, -1, 0, 0, -1, null, this, hash, pfbFile.lastModified()));

            Log.d("PdfBox-Android", "PFB: '" + type1.getName() + "' / '" + type1.getFamilyName() + "' / '" +
                    type1.getWeight() + "'");
        }
        catch (IOException e)
        {
            fontInfoList.add(createFSIgnored(pfbFile, FontFormat.PFB, "*skipexception*"));
            Log.w("PdfBox-Android", "Could not load font file: " + pfbFile, e);
        }
    }

    @Override
    public String toDebugString()
    {
        StringBuilder sb = new StringBuilder();
        for (FSFontInfo info : fontInfoList)
        {
            sb.append(info.getFormat());
            sb.append(": ");
            sb.append(info.getPostScriptName());
            sb.append(": ");
            sb.append(info.file.getPath());
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public List<? extends FontInfo> getFontInfo()
    {
        return fontInfoList;
    }

    // closes the input
    // doesn't use readAllBytes() because some fonts are huge (PDFBOX-5781)
    private static String computeHash(InputStream is) throws IOException
    {
        CRC32 crc = new CRC32();

        try (is)
        {
            byte[] buffer = new byte[4096];
            int readBytes;
            while ((readBytes = is.read(buffer)) != -1)
            {
                crc.update(buffer, 0, readBytes);
            }

            long hash = crc.getValue();
            return Long.toHexString(hash);
        }
    }
}
