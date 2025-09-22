package com.tom_roush.fontbox.ttf.table.common;

public class FeatureRecord
{
    private final String featureTag;
    private final FeatureTable featureTable;

    public FeatureRecord(String featureTag, FeatureTable featureTable)
    {
        this.featureTag = featureTag;
        this.featureTable = featureTable;
    }

    public String getFeatureTag()
    {
        return featureTag;
    }

    public FeatureTable getFeatureTable()
    {
        return featureTable;
    }

    @Override
    public String toString()
    {
        return String.format("FeatureRecord[featureTag=%s]", featureTag);
    }
}