package com.example.TextileManagement.service;

import java.util.ArrayList;
import java.util.List;

import com.example.TextileManagement.entities.TakaEntry;

public final class ChallanLayoutPlanner {
    public static final int COLUMNS_PER_CHALLAN = 4;
    public static final int ROWS_PER_COLUMN = 12;
    public static final int MAX_TAKAS_PER_CHALLAN = COLUMNS_PER_CHALLAN * ROWS_PER_COLUMN;
    public static final double TARGET_METERS_PER_COLUMN = 1200d;

    private ChallanLayoutPlanner() {
    }

    public static int pageCount(List<TakaEntry> entries, boolean balanceColumnsByMeters) {
        return plan(entries, balanceColumnsByMeters).size();
    }

    public static List<ChallanPage> plan(List<TakaEntry> entries, boolean balanceColumnsByMeters) {
        List<TakaEntry> takas = entries == null ? List.of() : entries;
        List<List<TakaEntry>> columns = balanceColumnsByMeters
                ? splitColumnsNearTarget(takas)
                : splitColumnsByCapacity(takas);
        if (columns.isEmpty()) {
            columns.add(new ArrayList<>());
        }

        List<ChallanPage> pages = new ArrayList<>();
        for (int start = 0; start < columns.size(); start += COLUMNS_PER_CHALLAN) {
            List<List<TakaEntry>> pageColumns = new ArrayList<>();
            for (int column = 0; column < COLUMNS_PER_CHALLAN; column++) {
                int columnIndex = start + column;
                pageColumns.add(columnIndex < columns.size()
                        ? new ArrayList<>(columns.get(columnIndex))
                        : new ArrayList<>());
            }
            pages.add(new ChallanPage(pageColumns));
        }
        return pages;
    }

    private static List<List<TakaEntry>> splitColumnsByCapacity(List<TakaEntry> takas) {
        List<List<TakaEntry>> columns = new ArrayList<>();
        for (int start = 0; start < takas.size(); start += ROWS_PER_COLUMN) {
            int end = Math.min(start + ROWS_PER_COLUMN, takas.size());
            columns.add(new ArrayList<>(takas.subList(start, end)));
        }
        return columns;
    }

    private static List<List<TakaEntry>> splitColumnsNearTarget(List<TakaEntry> takas) {
        List<List<TakaEntry>> columns = new ArrayList<>();
        int nextEntry = 0;
        while (nextEntry < takas.size()) {
            List<TakaEntry> column = new ArrayList<>();
            double totalMeters = 0d;
            while (nextEntry < takas.size() && column.size() < ROWS_PER_COLUMN) {
                TakaEntry nextTaka = takas.get(nextEntry);
                double nextMeters = meters(nextTaka);
                if (!column.isEmpty() && totalMeters + nextMeters >= TARGET_METERS_PER_COLUMN) {
                    double belowTarget = TARGET_METERS_PER_COLUMN - totalMeters;
                    double aboveTarget = totalMeters + nextMeters - TARGET_METERS_PER_COLUMN;
                    if (belowTarget < aboveTarget) {
                        break;
                    }
                }
                column.add(nextTaka);
                nextEntry++;
                totalMeters += nextMeters;
                if (totalMeters >= TARGET_METERS_PER_COLUMN) {
                    break;
                }
            }
            columns.add(column);
        }
        return columns;
    }

    private static double meters(TakaEntry taka) {
        return taka == null || taka.getMeters() == null ? 0d : taka.getMeters();
    }

    public record ChallanPage(List<List<TakaEntry>> columns) {
        public ChallanPage {
            columns = columns.stream().<List<TakaEntry>>map(column -> new ArrayList<>(column)).toList();
        }

        public List<TakaEntry> entries() {
            return columns.stream().flatMap(List::stream).toList();
        }
    }
}
