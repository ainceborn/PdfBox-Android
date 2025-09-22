package com.tom_roush.fontbox.ttf.table;

public class LigatureSetTable
{
    private final int ligatureCount;
    private final LigatureTable[] ligatureTables;

    public LigatureSetTable(int ligatureCount, LigatureTable[] ligatureTables)
    {
        this.ligatureCount = ligatureCount;
        this.ligatureTables = ligatureTables;
    }

    public int getLigatureCount()
    {
        return ligatureCount;
    }

    public LigatureTable[] getLigatureTables()
    {
        return ligatureTables;
    }

    @Override
    public String toString()
    {
        return String.format("%s[ligatureCount=%d]", LigatureSetTable.class.getSimpleName(),
                ligatureCount);
    }
}