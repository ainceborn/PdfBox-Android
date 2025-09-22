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

package com.tom_roush.fontbox.ttf;

import android.util.Log;

import com.tom_roush.fontbox.ttf.table.AlternateSetTable;
import com.tom_roush.fontbox.ttf.table.CoverageTable;
import com.tom_roush.fontbox.ttf.table.CoverageTableFormat1;
import com.tom_roush.fontbox.ttf.table.CoverageTableFormat2;
import com.tom_roush.fontbox.ttf.table.FeatureListTable;
import com.tom_roush.fontbox.ttf.table.LangSysTable;
import com.tom_roush.fontbox.ttf.table.LigatureSetTable;
import com.tom_roush.fontbox.ttf.table.LigatureTable;
import com.tom_roush.fontbox.ttf.table.LookupSubTable;
import com.tom_roush.fontbox.ttf.table.LookupTable;
import com.tom_roush.fontbox.ttf.table.LookupTypeAlternateSubstitutionFormat1;
import com.tom_roush.fontbox.ttf.table.LookupTypeLigatureSubstitutionSubstFormat1;
import com.tom_roush.fontbox.ttf.table.LookupTypeMultipleSubstitutionFormat1;
import com.tom_roush.fontbox.ttf.table.LookupTypeSingleSubstFormat1;
import com.tom_roush.fontbox.ttf.table.LookupTypeSingleSubstFormat2;
import com.tom_roush.fontbox.ttf.table.RangeRecord;
import com.tom_roush.fontbox.ttf.table.ScriptTable;
import com.tom_roush.fontbox.ttf.table.SequenceTable;
import com.tom_roush.fontbox.ttf.table.common.FeatureRecord;
import com.tom_roush.fontbox.ttf.table.common.FeatureTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A glyph substitution 'GSUB' table in a TrueType or OpenType font.
 *
 * @author Aaron Madlon-Kay
 */
public class GlyphSubstitutionTable extends TTFTable
{
    public static final String TAG = "GSUB";

    private LinkedHashMap<String, ScriptTable> scriptList;
    // featureList and lookupList are not maps because we need to index into them
    private FeatureListTable featureListTable;
    private LookupTable[] lookupList;

    private final Map<Integer, Integer> lookupCache = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> reverseLookup = new HashMap<Integer, Integer>();

    private String lastUsedSupportedScript;

    private static final Predicate<String> IS_4_CHAR_WORD = s -> s != null && Pattern.compile("\\w{4}").matcher(s).matches();

    GlyphSubstitutionTable(TrueTypeFont font)
    {
        super(font);
    }

    @Override
    void read(TrueTypeFont ttf, TTFDataStream data) throws IOException
    {
        long start = data.getCurrentPosition();
        @SuppressWarnings("unused")
        int majorVersion = data.readUnsignedShort();
        int minorVersion = data.readUnsignedShort();
        int scriptListOffset = data.readUnsignedShort();
        int featureListOffset = data.readUnsignedShort();
        int lookupListOffset = data.readUnsignedShort();
        @SuppressWarnings("unused")
        long featureVariationsOffset = -1L;
        if (minorVersion == 1L)
        {
            featureVariationsOffset = data.readUnsignedInt();
        }

        scriptList = readScriptList(data, start + scriptListOffset);
        featureListTable = readFeatureList(data, start + featureListOffset);
        lookupList = readLookupList(data, start + lookupListOffset);
    }

    LinkedHashMap<String, ScriptTable> readScriptList(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int scriptCount = data.readUnsignedShort();
        ScriptRecord[] scriptRecords = new ScriptRecord[scriptCount];
        int[] scriptOffsets = new int[scriptCount];
        for (int i = 0; i < scriptCount; i++)
        {
            ScriptRecord scriptRecord = new ScriptRecord();
            scriptRecord.scriptTag = data.readString(4);
            scriptOffsets[i] = data.readUnsignedShort();
            scriptRecords[i] = scriptRecord;
        }
        for (int i = 0; i < scriptCount; i++)
        {
            scriptRecords[i].scriptTable = readScriptTable(data, offset + scriptOffsets[i]);
        }
        LinkedHashMap<String, ScriptTable> resultScriptList = new LinkedHashMap<String, ScriptTable>(scriptCount);
        for (ScriptRecord scriptRecord : scriptRecords)
        {
            resultScriptList.put(scriptRecord.scriptTag, scriptRecord.scriptTable);
        }
        return resultScriptList;
    }

    ScriptTable readScriptTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int defaultLangSys = data.readUnsignedShort();
        int langSysCount = data.readUnsignedShort();
        String[] langSysTags = new String[langSysCount];
        int[] langSysOffsets = new int[langSysCount];
        for (int i = 0; i < langSysCount; i++)
        {
            langSysTags[i] = data.readString(4);
            langSysOffsets[i] = data.readUnsignedShort();
            if (langSysOffsets[i] < data.getCurrentPosition() - offset)
            {
                // can't be before the current position
                Log.e("langSysOffsets", "can't be before the current position");
                return new ScriptTable(null, new LinkedHashMap<>());
            }
            if (i > 0 && langSysTags[i].compareTo(langSysTags[i-1]) < 0)
            {
                // PDFBOX-4489: catch corrupt file
                // https://docs.microsoft.com/en-us/typography/opentype/spec/chapter2#slTbl_sRec
                Log.e("langSysOffsets", "LangSysRecords not alphabetically sorted by LangSys tag:");
                return new ScriptTable(null, new LinkedHashMap<>());
            }
        }

        LangSysTable defaultLangSysTable = null;

        if (defaultLangSys != 0)
        {
            defaultLangSysTable = readLangSysTable(data, offset + defaultLangSys);
        }
        Map<String, LangSysTable> langSysTables = new LinkedHashMap<>(langSysCount);
        for (int i = 0; i < langSysCount; i++)
        {
            LangSysTable langSysTable = readLangSysTable(data, offset + langSysOffsets[i]);
            langSysTables.put(langSysTags[i], langSysTable);
        }
        return new ScriptTable(defaultLangSysTable, Collections.unmodifiableMap(langSysTables));
    }

    LangSysTable readLangSysTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int lookupOrder = data.readUnsignedShort();
        int requiredFeatureIndex = data.readUnsignedShort();
        int featureIndexCount = data.readUnsignedShort();
        int[] featureIndices = new int[featureIndexCount];
        for (int i = 0; i < featureIndexCount; i++)
        {
            featureIndices[i] = data.readUnsignedShort();
        }
        return new LangSysTable(lookupOrder, requiredFeatureIndex, featureIndexCount,
                featureIndices);
    }

    FeatureListTable readFeatureList(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        String prevFeatureTag = "";
        int featureCount = data.readUnsignedShort();
        FeatureRecord[] featureRecords = new FeatureRecord[featureCount];
        int[] featureOffsets = new int[featureCount];
        String[] featureTags = new String[featureCount];
        for (int i = 0; i < featureCount; i++)
        {
            featureTags[i] = data.readString(4);
            var featureTag = data.readString(4);
            if (i > 0 && featureTags[i].compareTo(featureTags[i-1]) < 0)
            {
                // catch corrupt file
                // https://docs.microsoft.com/en-us/typography/opentype/spec/chapter2#flTbl
                if (IS_4_CHAR_WORD.test(featureTags[i]) && IS_4_CHAR_WORD.test(featureTags[i - 1]))
                {
                    // ArialUni.ttf has many warnings but isn't corrupt, so we assume that only
                    // strings with trash characters indicate real corruption
                    Log.d("PdfBox-Android", "FeatureRecord array not alphabetically sorted by FeatureTag: " +
                            IS_4_CHAR_WORD.toString() + " < " + prevFeatureTag);
                }
                else
                {
                    Log.w("PdfBox-Android", "FeatureRecord array not alphabetically sorted by FeatureTag: " +
                            IS_4_CHAR_WORD.toString() + " < " + prevFeatureTag);
                    return new FeatureListTable(0, new FeatureRecord[0]);
                }
            }
            featureOffsets[i] = data.readUnsignedShort();
            prevFeatureTag = featureTag;
        }
        for (int i = 0; i < featureCount; i++)
        {
            FeatureTable featureTable = readFeatureTable(data, offset + featureOffsets[i]);
            featureRecords[i] = new FeatureRecord(featureTags[i], featureTable);
        }
        return new FeatureListTable(featureCount, featureRecords);
    }

    FeatureTable readFeatureTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int featureParams = data.readUnsignedShort();
        int lookupIndexCount = data.readUnsignedShort();
        int[] lookupListIndices = new int[lookupIndexCount];
        for (int i = 0; i < lookupIndexCount; i++)
        {
            lookupListIndices[i] = data.readUnsignedShort();
        }
        return new FeatureTable(featureParams, lookupIndexCount, lookupListIndices);
    }

    LookupTable[] readLookupList(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int lookupCount = data.readUnsignedShort();
        int[] lookups = new int[lookupCount];
        for (int i = 0; i < lookupCount; i++)
        {
            lookups[i] = data.readUnsignedShort();
        }
        LookupTable[] lookupTables = new LookupTable[lookupCount];
        for (int i = 0; i < lookupCount; i++)
        {
            lookupTables[i] = readLookupTable(data, offset + lookups[i]);
        }
        return lookupTables;
    }

    LookupTable readLookupTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int lookupType = data.readUnsignedShort();
        int lookupFlag = data.readUnsignedShort();
        int subTableCount = data.readUnsignedShort();
        int[] subTableOffsets = new int[subTableCount];
        for (int i = 0; i < subTableCount; i++)
        {
            subTableOffsets[i] = data.readUnsignedShort();
            if (subTableOffsets[i] == 0)
            {
                Log.e("subTableOffsets", "subTableOffsets[{}] is 0 at offset {} "+ i + " | " + (data.getCurrentPosition() - 2));
                return new LookupTable(lookupType, lookupFlag, 0, new LookupSubTable[0]);
            }
            if (offset + subTableOffsets[i] > data.getOriginalDataSize())
            {
                Log.e("subTableOffsets", " > {} " + offset + subTableOffsets[i] + " | " + data.getOriginalDataSize());
                return new LookupTable(lookupType, lookupFlag, 0, new LookupSubTable[0]);
            }
        }

        int markFilteringSet;
        if ((lookupFlag & 0x0010) != 0)
        {
            markFilteringSet = data.readUnsignedShort();
        }
        else
        {
            markFilteringSet = 0;
        }
        LookupSubTable[] subTables = new LookupSubTable[subTableCount];
        switch (lookupType)
        {
            case 1:
            case 2:
            case 3:
            case 4:
                for (int i = 0; i < subTableCount; i++)
                {
                    subTables[i] = readLookupSubtable(data, offset + subTableOffsets[i], lookupType);
                }
                break;
            case 7:
                // Extension Substitution
                // https://learn.microsoft.com/en-us/typography/opentype/spec/gsub#ES
                for (int i = 0; i < subTableCount; i++)
                {
                    data.seek(offset + subTableOffsets[i]);
                    int substFormat = data.readUnsignedShort(); // always 1
                    if (substFormat != 1)
                    {
                        Log.e("readLookupTable", "The expected SubstFormat for ExtensionSubstFormat1 subtable is {} but should be 1 at offset {} " + substFormat  + " | " + offset + subTableOffsets[i]);
                        continue;
                    }
                    int extensionLookupType = data.readUnsignedShort();
                    if (lookupType != 7 && lookupType != extensionLookupType)
                    {
                        // "If a lookup table uses extension subtables, then all of the extension
                        //  subtables must have the same extensionLookupType"
                        Log.e("readLookupTable", "extensionLookupType changed from {} to {} at offset {} " + lookupType + " | " + extensionLookupType + "| " + offset + " | " + subTableOffsets[i] + 2);
                        continue;
                    }
                    lookupType = extensionLookupType;
                    long extensionOffset = data.readUnsignedInt();
                    long extensionLookupTableAddress = offset + subTableOffsets[i] + extensionOffset;
                    subTables[i] = readLookupSubtable(data, extensionLookupTableAddress, extensionLookupType);
                }
                break;
            default:
                // Other lookup types are not supported
                Log.e("readLookupTable", "Type {} GSUB lookup table is not supported and will be ignored: "+ lookupType);
        }
        return new LookupTable(lookupType, lookupFlag, markFilteringSet, subTables);
    }

    private LookupSubTable readLookupSubtable(TTFDataStream data, long offset, int lookupType) throws IOException
    {
        switch (lookupType)
        {
            case 1:
                // Single Substitution Subtable
                // https://docs.microsoft.com/en-us/typography/opentype/spec/gsub#SS
                return readSingleLookupSubTable(data, offset);
            case 2:
                // Multiple Substitution Subtable
                // https://learn.microsoft.com/en-us/typography/opentype/spec/gsub#lookuptype-2-multiple-substitution-subtable
                return readMultipleSubstitutionSubtable(data, offset);
            case 3:
                // Alternate Substitution Subtable
                // https://learn.microsoft.com/en-us/typography/opentype/spec/gsub#lookuptype-3-alternate-substitution-subtable
                return readAlternateSubstitutionSubtable(data, offset);
            case 4:
                // Ligature Substitution Subtable
                // https://docs.microsoft.com/en-us/typography/opentype/spec/gsub#LS
                return readLigatureSubstitutionSubtable(data, offset);

            // when creating a new LookupSubTable derived type, don't forget to add a "switch"
            // in readLookupTable() and add the type in GlyphSubstitutionDataExtractor.extractData()

            default:
                // Other lookup types are not supported
                Log.d( "readLookupSubtable","Type {} GSUB lookup table is not supported and will be ignored: " + lookupType);
                return null;
            //TODO next: implement type 6
            // https://learn.microsoft.com/en-us/typography/opentype/spec/gsub#lookuptype-6-chained-contexts-substitution-subtable
            // see e.g. readChainedContextualSubTable in Apache FOP
            // https://github.com/apache/xmlgraphics-fop/blob/1323c2e3511eb23c7dd9b8fb74463af707fa972d/fop-core/src/main/java/org/apache/fop/complexscripts/fonts/OTFAdvancedTypographicTableReader.java#L898
        }
    }

    private LookupSubTable readSingleLookupSubTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int substFormat = data.readUnsignedShort();
        switch (substFormat)
        {
            case 1:
            {
                // LookupType 1: Single Substitution Subtable
                // https://docs.microsoft.com/en-us/typography/opentype/spec/gsub#11-single-substitution-format-1
                int coverageOffset = data.readUnsignedShort();
                short deltaGlyphID = data.readSignedShort();
                CoverageTable coverageTable = readCoverageTable(data, offset + coverageOffset);
                return new LookupTypeSingleSubstFormat1(substFormat, coverageTable, deltaGlyphID);
            }
            case 2:
            {
                int coverageOffset = data.readUnsignedShort();
                int glyphCount = data.readUnsignedShort();
                int[] substituteGlyphIDs = new int[glyphCount];
                for (int i = 0; i < glyphCount; i++)
                {
                    substituteGlyphIDs[i] = data.readUnsignedShort();
                }
                CoverageTable coverageTable = readCoverageTable(data, offset + coverageOffset);
                return new LookupTypeSingleSubstFormat2(substFormat, coverageTable, substituteGlyphIDs);
            }
            default:
                return null;
        }
    }

    private LookupSubTable readMultipleSubstitutionSubtable(TTFDataStream data, long offset)
            throws IOException
    {
        data.seek(offset);
        int substFormat = data.readUnsignedShort();

        if (substFormat != 1)
        {
            throw new IOException(
                    "The expected SubstFormat for LigatureSubstitutionTable is 1");
        }

        int coverage = data.readUnsignedShort();
        int sequenceCount = data.readUnsignedShort();
        int[] sequenceOffsets = new int[sequenceCount];
        for (int i = 0; i < sequenceCount; i++)
        {
            sequenceOffsets[i] = data.readUnsignedShort();
        }

        CoverageTable coverageTable = readCoverageTable(data, offset + coverage);

        if (sequenceCount != coverageTable.getSize())
        {
            throw new IOException(
                    "According to the OpenTypeFont specifications, the coverage count should be equal to the no. of SequenceTables");
        }

        SequenceTable[] sequenceTables = new SequenceTable[sequenceCount];
        for (int i = 0; i < sequenceCount; i++)
        {
            data.seek(offset + sequenceOffsets[i]);
            int glyphCount = data.readUnsignedShort();
            int[] substituteGlyphIDs = data.readUnsignedShortArray(glyphCount);
            sequenceTables[i] = new SequenceTable(glyphCount, substituteGlyphIDs);
        }

        return new LookupTypeMultipleSubstitutionFormat1(substFormat, coverageTable, sequenceTables);
    }

    private LookupSubTable readAlternateSubstitutionSubtable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int substFormat = data.readUnsignedShort();

        if (substFormat != 1)
        {
            throw new IOException(
                    "The expected SubstFormat for AlternateSubstitutionTable is 1");
        }

        int coverage = data.readUnsignedShort();
        int altSetCount = data.readUnsignedShort();

        int[] alternateOffsets = new int[altSetCount];

        for (int i = 0; i < altSetCount; i++)
        {
            alternateOffsets[i] = data.readUnsignedShort();
        }

        CoverageTable coverageTable = readCoverageTable(data, offset + coverage);

        if (altSetCount != coverageTable.getSize())
        {
            throw new IOException(
                    "According to the OpenTypeFont specifications, the coverage count should be equal to the no. of AlternateSetTable");
        }

        AlternateSetTable[] alternateSetTables = new AlternateSetTable[altSetCount];

        for (int i = 0; i < altSetCount; i++)
        {
            data.seek(offset + alternateOffsets[i]);
            int glyphCount = data.readUnsignedShort();
            int[] alternateGlyphIDs = data.readUnsignedShortArray(glyphCount);
            alternateSetTables[i] = new AlternateSetTable(glyphCount, alternateGlyphIDs);
        }

        return new LookupTypeAlternateSubstitutionFormat1(substFormat, coverageTable,
                alternateSetTables);
    }

    private LookupSubTable readLigatureSubstitutionSubtable(TTFDataStream data, long offset)
            throws IOException
    {
        data.seek(offset);
        int substFormat = data.readUnsignedShort();

        if (substFormat != 1)
        {
            throw new IOException(
                    "The expected SubstFormat for LigatureSubstitutionTable is 1");
        }

        int coverage = data.readUnsignedShort();
        int ligSetCount = data.readUnsignedShort();

        int[] ligatureOffsets = new int[ligSetCount];

        for (int i = 0; i < ligSetCount; i++)
        {
            ligatureOffsets[i] = data.readUnsignedShort();
        }

        CoverageTable coverageTable = readCoverageTable(data, offset + coverage);

        if (ligSetCount != coverageTable.getSize())
        {
            throw new IOException(
                    "According to the OpenTypeFont specifications, the coverage count should be equal to the no. of LigatureSetTables");
        }

        LigatureSetTable[] ligatureSetTables = new LigatureSetTable[ligSetCount];

        for (int i = 0; i < ligSetCount; i++)
        {

            int coverageGlyphId = coverageTable.getGlyphId(i);

            ligatureSetTables[i] = readLigatureSetTable(data,
                    offset + ligatureOffsets[i], coverageGlyphId);
        }

        return new LookupTypeLigatureSubstitutionSubstFormat1(substFormat, coverageTable,
                ligatureSetTables);
    }

    private LigatureSetTable readLigatureSetTable(TTFDataStream data, long ligatureSetTableLocation,
                                                  int coverageGlyphId) throws IOException
    {
        data.seek(ligatureSetTableLocation);

        int ligatureCount = data.readUnsignedShort();

        int[] ligatureOffsets = new int[ligatureCount];
        LigatureTable[] ligatureTables = new LigatureTable[ligatureCount];

        for (int i = 0; i < ligatureOffsets.length; i++)
        {
            ligatureOffsets[i] = data.readUnsignedShort();
        }

        for (int i = 0; i < ligatureOffsets.length; i++)
        {
            int ligatureOffset = ligatureOffsets[i];
            ligatureTables[i] = readLigatureTable(data,
                    ligatureSetTableLocation + ligatureOffset, coverageGlyphId);
        }

        return new LigatureSetTable(ligatureCount, ligatureTables);
    }

    private LigatureTable readLigatureTable(TTFDataStream data, long ligatureTableLocation,
                                            int coverageGlyphId) throws IOException
    {
        data.seek(ligatureTableLocation);

        int ligatureGlyph = data.readUnsignedShort();

        int componentCount = data.readUnsignedShort();
        if (componentCount > 100)
        {
            throw new IOException("componentCount in ligature table is " +
                    componentCount + ", font likely corrupt");
        }

        int[] componentGlyphIDs = new int[componentCount];

        if (componentCount > 0)
        {
            componentGlyphIDs[0] = coverageGlyphId;
        }

        for (int i = 1; i <= componentCount - 1; i++)
        {
            componentGlyphIDs[i] = data.readUnsignedShort();
        }

        return new LigatureTable(ligatureGlyph, componentCount, componentGlyphIDs);

    }

    private CoverageTable readCoverageTable(TTFDataStream data, long offset) throws IOException
    {
        data.seek(offset);
        int coverageFormat = data.readUnsignedShort();
        switch (coverageFormat)
        {
            case 1:
            {
                int glyphCount = data.readUnsignedShort();
                int[] glyphArray = new int[glyphCount];
                for (int i = 0; i < glyphCount; i++)
                {
                    glyphArray[i] = data.readUnsignedShort();
                }
                return new CoverageTableFormat1(coverageFormat, glyphArray);
            }
            case 2:
            {
                int rangeCount = data.readUnsignedShort();
                RangeRecord[] rangeRecords = new RangeRecord[rangeCount];


                for (int i = 0; i < rangeCount; i++)
                {
                    rangeRecords[i] = readRangeRecord(data);
                }

                return new CoverageTableFormat2(coverageFormat, rangeRecords);
            }
            default:
                // Should not happen (the spec indicates only format 1 and format 2)
                throw new IOException("Unknown coverage format: " + coverageFormat);
        }
    }


    /**
     * Choose from one of the supplied OpenType script tags, depending on what the font supports and
     * potentially on context.
     *
     * @param tags
     * @return The best OpenType script tag
     */
    private String selectScriptTag(String[] tags)
    {
        if (tags.length == 1)
        {
            String tag = tags[0];
            if (OpenTypeScript.INHERITED.equals(tag)
                || (OpenTypeScript.TAG_DEFAULT.equals(tag) && !scriptList.containsKey(tag)))
            {
                // We don't know what script this should be.
                if (lastUsedSupportedScript == null)
                {
                    // We have no past context and (currently) no way to get future context so we guess.
                    lastUsedSupportedScript = scriptList.keySet().iterator().next();
                }
                // else use past context

                return lastUsedSupportedScript;
            }
        }
        for (String tag : tags)
        {
            if (scriptList.containsKey(tag))
            {
                // Use the first recognized tag. We assume a single font only recognizes one version ("ver. 2")
                // of a single script, or if it recognizes more than one that it prefers the latest one.
                lastUsedSupportedScript = tag;
                return lastUsedSupportedScript;
            }
        }
        return tags[0];
    }

    private Collection<LangSysTable> getLangSysTables(String scriptTag)
    {
        Collection<LangSysTable> result = Collections.emptyList();
        ScriptTable scriptTable = scriptList.get(scriptTag);
        if (scriptTable != null)
        {
            if (scriptTable.getDefaultLangSysTable() == null)
            {
                result = scriptTable.getLangSysTables().values();
            }
            else
            {
                result = new ArrayList<>(scriptTable.getLangSysTables().values());
                result.add(scriptTable.getDefaultLangSysTable());
            }
        }
        return result;
    }

    /**
     * Get a list of {@code FeatureRecord}s from a collection of {@code LangSysTable}s. Optionally
     * filter the returned features by supplying a list of allowed feature tags in
     * {@code enabledFeatures}.
     *
     * Note that features listed as required ({@code LangSysTable#requiredFeatureIndex}) will be
     * included even if not explicitly enabled.
     *
     * @param langSysTables The {@code LangSysTable}s indicating {@code FeatureRecord}s to search
     * for
     * @param enabledFeatures An optional list of feature tags ({@code null} to allow all)
     * @return The indicated {@code FeatureRecord}s
     */
    private List<FeatureRecord> getFeatureRecords(Collection<LangSysTable> langSysTables,
        final List<String> enabledFeatures)
    {
        if (langSysTables.isEmpty())
        {
            return Collections.emptyList();
        }
        List<FeatureRecord> result = new ArrayList<>();
        langSysTables.forEach(langSysTable ->
        {
            int required = langSysTable.getRequiredFeatureIndex();
            FeatureRecord[] featureRecords = featureListTable.getFeatureRecords();
            if (required != 0xffff && required < featureRecords.length) // if no required features = 0xFFFF
            {
                result.add(featureRecords[required]);
            }
            for (int featureIndex : langSysTable.getFeatureIndices())
            {
                if (featureIndex < featureRecords.length &&
                        (enabledFeatures == null ||
                                enabledFeatures.contains(featureRecords[featureIndex].getFeatureTag())))
                {
                    result.add(featureRecords[featureIndex]);
                }
            }
        });

        // 'vrt2' supersedes 'vert' and they should not be used together
        // https://www.microsoft.com/typography/otspec/features_uz.htm
        if (containsFeature(result, "vrt2"))
        {
            removeFeature(result, "vert");
        }

        if (enabledFeatures != null && result.size() > 1)
        {
            result.sort(Comparator.comparingInt(o -> enabledFeatures.indexOf(o.getFeatureTag())));
        }

        return result;
    }

    private boolean containsFeature(List<FeatureRecord> featureRecords, String featureTag)
    {
        for (FeatureRecord featureRecord : featureRecords)
        {
            if (featureRecord.getFeatureTag().equals(featureTag))
            {
                return true;
            }
        }
        return false;
    }

    private void removeFeature(List<FeatureRecord> featureRecords, String featureTag)
    {
        Iterator<FeatureRecord> iter = featureRecords.iterator();
        while (iter.hasNext())
        {
            if (iter.next().getFeatureTag().equals(featureTag))
            {
                iter.remove();
            }
        }
    }

    private int applyFeature(FeatureRecord featureRecord, int gid)
    {
        int lookupResult = gid;
        for (int lookupListIndex : featureRecord.getFeatureTable().getLookupListIndices())
        {
            LookupTable lookupTable = lookupList[lookupListIndex];
            if (lookupTable.getLookupType() != 1)
            {
                Log.d("PdfBox-Android", "Skipping GSUB feature '" + featureRecord.getFeatureTag()
                        + "' because it requires unsupported lookup table type " + lookupTable.getLookupType());
                continue;
            }
            lookupResult = doLookup(lookupTable, lookupResult);
        }
        return lookupResult;
    }

    private int doLookup(LookupTable lookupTable, int gid)
    {
        for (LookupSubTable lookupSubtable : lookupTable. getSubTables())
        {
            int coverageIndex = lookupSubtable.getCoverageTable().getCoverageIndex(gid);
            if (coverageIndex >= 0)
            {
                return lookupSubtable.doSubstitution(gid, coverageIndex);
            }
        }
        return gid;
    }

    /**
     * Apply glyph substitutions to the supplied gid. The applicable substitutions are determined by
     * the {@code scriptTags} which indicate the language of the gid, and by the list of
     * {@code enabledFeatures}.
     *
     * To ensure that a single gid isn't mapped to multiple substitutions, subsequent invocations
     * with the same gid will return the same result as the first, regardless of script or enabled
     * features.
     *
     * @param gid GID
     * @param scriptTags Script tags applicable to the gid (see {@link OpenTypeScript})
     * @param enabledFeatures list of features to apply
     */
    public int getSubstitution(int gid, String[] scriptTags, List<String> enabledFeatures)
    {
        if (gid == -1)
        {
            return -1;
        }
        Integer cached = lookupCache.get(gid);
        if (cached != null)
        {
            // Because script detection for indeterminate scripts (COMMON, INHERIT, etc.) depends on context,
            // it is possible to return a different substitution for the same input. However, we don't want that,
            // as we need a one-to-one mapping.
            return cached;
        }
        String scriptTag = selectScriptTag(scriptTags);
        Collection<LangSysTable> langSysTables = getLangSysTables(scriptTag);
        List<FeatureRecord> featureRecords = getFeatureRecords(langSysTables, enabledFeatures);
        int sgid = gid;
        for (FeatureRecord featureRecord : featureRecords)
        {
            sgid = applyFeature(featureRecord, sgid);
        }
        lookupCache.put(gid, sgid);
        reverseLookup.put(sgid, gid);
        return sgid;
    }

    /**
     * For a substitute-gid (obtained from {@link #getSubstitution(int, String[], List)}), retrieve
     * the original gid.
     *
     * Only gids previously substituted by this instance can be un-substituted. If you are trying to
     * unsubstitute before you substitute, something is wrong.
     *
     * @param sgid Substitute GID
     */
    public int getUnsubstitution(int sgid)
    {
        Integer gid = reverseLookup.get(sgid);
        if (gid == null)
        {
            Log.w("PdfBox-Android", "Trying to un-substitute a never-before-seen gid: " + sgid);
            return sgid;
        }
        return gid;
    }

    RangeRecord readRangeRecord(TTFDataStream data) throws IOException
    {
        int startGlyphID = data.readUnsignedShort();
        int endGlyphID = data.readUnsignedShort();
        int startCoverageIndex = data.readUnsignedShort();
        return new RangeRecord(startGlyphID, endGlyphID, startCoverageIndex);
    }


    static class ScriptRecord
    {
        // https://www.microsoft.com/typography/otspec/scripttags.htm
        String scriptTag;
        public ScriptTable scriptTable;

        @Override
        public String toString()
        {
            return String.format("ScriptRecord[scriptTag=%s]", scriptTag);
        }
    }

    static class LangSysRecord
    {
        // https://www.microsoft.com/typography/otspec/languagetags.htm
        String langSysTag;
        LangSysTable langSysTable;

        @Override
        public String toString()
        {
            return String.format("LangSysRecord[langSysTag=%s]", langSysTag);
        }
    }
}
