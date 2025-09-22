package com.tom_roush.fontbox.ttf.table;

public class LookupTypeLigatureSubstitutionSubstFormat1 extends LookupSubTable
{
    private final LigatureSetTable[] ligatureSetTables;

    public LookupTypeLigatureSubstitutionSubstFormat1(int substFormat, CoverageTable coverageTable,
            LigatureSetTable[] ligatureSetTables)
    {
        super(substFormat, coverageTable);
        this.ligatureSetTables = ligatureSetTables;
    }

    @Override
    public int doSubstitution(int gid, int coverageIndex)
    {
        throw new UnsupportedOperationException("not applicable");
    }

    public LigatureSetTable[] getLigatureSetTables()
    {
        return ligatureSetTables;
    }

    @Override
    public String toString()
    {
        return String.format("%s[substFormat=%d]",
                LookupTypeLigatureSubstitutionSubstFormat1.class.getSimpleName(), getSubstFormat());
    }
}