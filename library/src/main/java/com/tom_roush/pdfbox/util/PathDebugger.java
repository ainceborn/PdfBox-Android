package com.tom_roush.pdfbox.util;

import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class PathDebugger {

    public static Boolean isEnabled = false;

    public static List<float[]> getPathPoints(Path path, float step) {
        List<float[]> points = new ArrayList<>();
        PathMeasure measure = new PathMeasure(path, false);
        float[] coords = new float[2];

        // обходим все контуры (если их несколько)
        do {
            float length = measure.getLength();
            float distance = 0f;
            while (distance <= length) {
                if (measure.getPosTan(distance, coords, null)) {
                    points.add(new float[]{coords[0], coords[1]});
                }
                distance += step;
            }
        } while (measure.nextContour());

        return points;
    }

    public static void logPath(Path path, float step) {
        if(isEnabled == false) return;
        List<float[]> points = getPathPoints(path, step);
        StringBuilder sb = new StringBuilder("Path points:\n");
        for (float[] p : points) {
            sb.append(String.format("x=%.2f, y=%.2f\n", p[0], p[1]));
        }
        Log.d("PathDebugger", sb.toString());
    }
}