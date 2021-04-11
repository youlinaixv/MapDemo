package com.example.mapdemo;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 处理得到的预测点坐标
public class PositionProcess {
    List<Float> yPos = new ArrayList<>();
    final float WRONG_CODE = -100;
    public float getDistance(Map<Integer, float[]> treeMap) {
        for (Map.Entry<Integer, float[]> entry: treeMap.entrySet()) {
            yPos.add(entry.getValue()[1]);
        }

        while (removeWrongPoint()) {
            // 循环执行removeWrongPoint，直到没有明显预测错误的点
        }

        if (yPos.isEmpty()) {
            return 0;
        }
        // 需要判断max-min还是min-max
        int sign = 1;
        sign = yPos.get(0) < yPos.get(yPos.size() - 1) ? 1 : -1;
        return length2ZoomLevel(Collections.max(yPos) - Collections.min(yPos)) * sign;
    }

    // 将预测的凝视长度映射到界面缩放的级别
    private float length2ZoomLevel(float length) {
        if (length >= 0 && length < 0.5) {
            return 0;
        } else if (length >= 1 && length < 2.5) {
            return 1;
        } else if (length >= 2.5 && length < 3.5) {
            return 2;
        } else {
            return 3;
        }
    }

    private boolean removeWrongPoint() {
        List<Integer> wrongPoint = new ArrayList<>();
        for (int i = 1; i < yPos.size() - 1; i++) {
            float cosValue =
                    computeCos(yPos.get(i - 1), yPos.get(i), yPos.get(i + 1));
            if (cosValue < 0) {
                wrongPoint.add(i);
            }
        }
        if (wrongPoint.isEmpty()) {
            return false;
        }
        for (int i = wrongPoint.size() - 1; i >= 0; i--) {
            yPos.remove((int) wrongPoint.get(i));
        }
        return true;
    }

    private float computeCos(float y1, float y2, float y3) {
        return 1 + y3 - y1 + (y3 - y2) * (y2 - y1);
    }
}
