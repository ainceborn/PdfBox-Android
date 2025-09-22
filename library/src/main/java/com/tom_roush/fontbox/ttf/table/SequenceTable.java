package com.tom_roush.fontbox.ttf.table;

import java.util.Arrays;

public class SequenceTable {
    private final int glyphCount;
    private final int[] substituteGlyphIDs;

    public SequenceTable(int glyphCount, int[] substituteGlyphIDs) {
        this.glyphCount = glyphCount;
        this.substituteGlyphIDs = substituteGlyphIDs;
    }

    public int getGlyphCount() {
        return glyphCount;
    }

    public int[] getSubstituteGlyphIDs() {
        return substituteGlyphIDs;
    }

    @Override
    public String toString() {
        return "SequenceTable{" + "glyphCount=" + glyphCount + ", substituteGlyphIDs=" + Arrays.toString(substituteGlyphIDs) + '}';
    }
}