package com.tom_roush.fontbox.ttf.table;

import java.util.Arrays;

public class LookupTypeSingleSubstFormat2 extends LookupSubTable
{
    private final int[] substituteGlyphIDs;

    public LookupTypeSingleSubstFormat2(int substFormat, CoverageTable coverageTable,
                                        int[] substituteGlyphIDs)
    {
        super(substFormat, coverageTable);
        this.substituteGlyphIDs = substituteGlyphIDs;
    }

    @Override
    public int doSubstitution(int gid, int coverageIndex)
    {
        return coverageIndex < 0 ? gid : substituteGlyphIDs[coverageIndex];
    }

    public int[] getSubstituteGlyphIDs()
    {
        return substituteGlyphIDs;
    }

    @Override
    public String toString()
    {
        return String.format(
                "LookupTypeSingleSubstFormat2[substFormat=%d,substituteGlyphIDs=%s]",
                getSubstFormat(), Arrays.toString(substituteGlyphIDs));
    }
}