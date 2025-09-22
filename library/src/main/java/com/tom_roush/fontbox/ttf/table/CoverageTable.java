package com.tom_roush.fontbox.ttf.table;

public abstract class CoverageTable {
    int coverageFormat;

    protected CoverageTable(int coverageFormat) {
        this.coverageFormat = coverageFormat;
    }

    public abstract int getCoverageIndex(int gid);

    public int getCoverageFormat() {
        return coverageFormat;
    }

    public abstract int getSize();

    public abstract int getGlyphId(int index);

}