package com.tom_roush.fontbox.ttf.table;

public class LookupTypeAlternateSubstitutionFormat1 extends LookupSubTable {
    private final AlternateSetTable[] alternateSetTables;

    public LookupTypeAlternateSubstitutionFormat1(
            int substFormat, CoverageTable coverageTable, AlternateSetTable[] alternateSetTables) {
        super(substFormat, coverageTable);
        this.alternateSetTables = alternateSetTables;
    }

    public AlternateSetTable[] getAlternateSetTables() {
        return alternateSetTables;
    }

    @Override
    public int doSubstitution(int gid, int coverageIndex) {
        throw new UnsupportedOperationException("not applicable");
    }
}
