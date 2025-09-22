package com.tom_roush.fontbox.ttf.table;

public class LookupTypeSingleSubstFormat1 extends LookupSubTable {
    short deltaGlyphID;

    public LookupTypeSingleSubstFormat1(int substFormat, CoverageTable coverageTable,
                                        short deltaGlyphID) {
        super(substFormat, coverageTable);
        this.deltaGlyphID = deltaGlyphID;
    }

    @Override
    public int doSubstitution(int gid, int coverageIndex) {
        return coverageIndex < 0 ? gid : gid + deltaGlyphID;
    }

    @Override
    public String toString() {
        return String.format("LookupTypeSingleSubstFormat1[substFormat=%d,deltaGlyphID=%d]", getSubstFormat(), deltaGlyphID);
    }
}