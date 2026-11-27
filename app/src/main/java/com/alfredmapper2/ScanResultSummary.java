package com.alfredmapper2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class ScanResultSummary {
    static final class Entry {
        final String name;
        final String identifier;
        final String detail;
        final int rssi;

        Entry(String name, String identifier, String detail, int rssi) {
            this.name = name;
            this.identifier = identifier;
            this.detail = detail;
            this.rssi = rssi;
        }
    }

    private ScanResultSummary() {}

    static String build(List<Entry> entries, boolean wifi) {
        List<Entry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, new Comparator<Entry>() {
            @Override
            public int compare(Entry first, Entry second) {
                int nameOrder = first.name.compareToIgnoreCase(second.name);
                return nameOrder != 0
                        ? nameOrder
                        : first.identifier.compareTo(second.identifier);
            }
        });

        StringBuilder summary = new StringBuilder();
        summary.append(wifi ? "ALFRED MAPPER 2 - WI-FI RESULTS\n"
                : "ALFRED MAPPER 2 - BLUETOOTH RESULTS\n");
        for (Entry entry : sorted) {
            summary.append("\n").append(entry.name)
                    .append("\n").append(entry.identifier)
                    .append("\n").append(entry.rssi).append(" dBm");
            if (wifi && entry.detail != null && !entry.detail.isEmpty()) {
                summary.append("\n").append(entry.detail);
            }
        }
        return summary.toString();
    }

    static String buildRadar(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, new Comparator<Entry>() {
            @Override
            public int compare(Entry first, Entry second) {
                int nameOrder = first.name.compareToIgnoreCase(second.name);
                return nameOrder != 0
                        ? nameOrder
                        : first.identifier.compareTo(second.identifier);
            }
        });

        StringBuilder summary = new StringBuilder();
        summary.append("ALFRED MAPPER 2 - RADAR RESULTS");
        for (Entry entry : sorted) {
            summary.append("\n\n").append(entry.detail)
                    .append("\n").append(entry.name)
                    .append("\n").append(entry.identifier)
                    .append("\n").append(entry.rssi).append(" dBm");
        }
        return summary.toString();
    }
}
