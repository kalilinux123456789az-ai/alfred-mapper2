package com.alfredmapper2;

import java.util.List;

final class RadarAccessibilityDescription {
    private RadarAccessibilityDescription() {}

    static String build(
            String chartDescription,
            String signalCount,
            List<String> strongestSignals,
            String emptyState,
            String moreSignals) {
        StringBuilder description = new StringBuilder(chartDescription)
                .append(' ')
                .append(signalCount);
        if (strongestSignals.isEmpty()) {
            description.append(' ').append(emptyState);
        } else {
            for (String signal : strongestSignals) {
                description.append(' ').append(signal);
            }
        }
        if (!moreSignals.isEmpty()) {
            description.append(' ').append(moreSignals);
        }
        return description.toString();
    }
}
