package com.tom_roush.fontbox.ttf.table;

import java.util.Arrays;

public class CoverageTableFormat1 extends CoverageTable {

    private final int[] glyphArray;

    public CoverageTableFormat1(int coverageFormat, int[] glyphArray) {
        super(coverageFormat);
        this.glyphArray = glyphArray;
    }

    @Override
    public int getCoverageIndex(int gid) {
        return Arrays.binarySearch(glyphArray, gid);
    }

    @Override
    public int getGlyphId(int index) {
        return glyphArray[index];
    }

    @Override
    public int getSize() {
        return glyphArray.length;
    }

    public int[] getGlyphArray() {
        return glyphArray;
    }

    @Override
    public String toString() {
        return String.format("CoverageTableFormat1[coverageFormat=%d,glyphArray=%s]",
                getCoverageFormat(), Arrays.toString(glyphArray));
    }
}