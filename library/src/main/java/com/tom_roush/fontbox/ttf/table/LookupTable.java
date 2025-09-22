package com.tom_roush.fontbox.ttf.table;

public class LookupTable
{
    private final int lookupType;
    private final int lookupFlag;
    private final int markFilteringSet;
    private final LookupSubTable[] subTables;

    public LookupTable(int lookupType, int lookupFlag, int markFilteringSet,
            LookupSubTable[] subTables)
    {
        this.lookupType = lookupType;
        this.lookupFlag = lookupFlag;
        this.markFilteringSet = markFilteringSet;
        this.subTables = subTables;
    }

    public int getLookupType()
    {
        return lookupType;
    }

    public int getLookupFlag()
    {
        return lookupFlag;
    }

    public int getMarkFilteringSet()
    {
        return markFilteringSet;
    }

    public LookupSubTable[] getSubTables()
    {
        return subTables;
    }

    @Override
    public String toString()
    {
        return String.format("LookupTable[lookupType=%d,lookupFlag=%d,markFilteringSet=%d]",
                lookupType, lookupFlag, markFilteringSet);
    }
}