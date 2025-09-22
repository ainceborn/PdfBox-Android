package com.tom_roush.fontbox.ttf.table;

import java.util.Arrays;

public class AlternateSetTable {
    private final int glyphCount;
    private final int[] alternateGlyphIDs;

    public AlternateSetTable(int glyphCount, int[] alternateGlyphIDs) {
        this.glyphCount = glyphCount;
        this.alternateGlyphIDs = alternateGlyphIDs;
    }

    public int getGlyphCount() {
        return glyphCount;
    }

    public int[] getAlternateGlyphIDs() {
        return alternateGlyphIDs;
    }

    @Override
    public String toString() {
        return "AlternateSetTable{" + "glyphCount=" + glyphCount + ", alternateGlyphIDs=" + Arrays.toString(alternateGlyphIDs) + '}';
    }
}