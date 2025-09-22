package com.tom_roush.fontbox.ttf.table;

public abstract class LookupSubTable
{
    protected final int substFormat;
    private final CoverageTable coverageTable;

    protected LookupSubTable(int substFormat, CoverageTable coverageTable)
    {
        this.substFormat = substFormat;
        this.coverageTable = coverageTable;
    }

    public abstract int doSubstitution(int gid, int coverageIndex);

    public int getSubstFormat()
    {
        return substFormat;
    }

    public CoverageTable getCoverageTable()
    {
        return coverageTable;
    }
}