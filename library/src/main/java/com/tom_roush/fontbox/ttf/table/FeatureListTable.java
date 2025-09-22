package com.tom_roush.fontbox.ttf.table;

import com.tom_roush.fontbox.ttf.table.common.FeatureRecord;

public class FeatureListTable
{
    private final int featureCount;
    private final FeatureRecord[] featureRecords;

    public FeatureListTable(int featureCount, FeatureRecord[] featureRecords)
    {
        this.featureCount = featureCount;
        this.featureRecords = featureRecords;
    }

    public int getFeatureCount()
    {
        return featureCount;
    }

    public FeatureRecord[] getFeatureRecords()
    {
        return featureRecords;
    }


    @Override
    public String toString()
    {
        return String.format("%s[featureCount=%d]", FeatureListTable.class.getSimpleName(),
                featureCount);
    }
}