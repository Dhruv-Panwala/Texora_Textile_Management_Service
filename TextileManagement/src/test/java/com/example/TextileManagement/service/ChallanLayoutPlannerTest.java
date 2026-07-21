package com.example.TextileManagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.TextileManagement.entities.TakaEntry;

class ChallanLayoutPlannerTest {
    @Test
    void balancesColumnsAtTheMeterTotalClosestToTwelveHundred() {
        List<TakaEntry> takas = List.of(taka(1, 600), taka(2, 590), taka(3, 100));

        ChallanLayoutPlanner.ChallanPage page = ChallanLayoutPlanner.plan(takas, true).get(0);

        assertEquals(List.of(1, 2), page.columns().get(0).stream().map(TakaEntry::getTakaNo).toList());
        assertEquals(List.of(3), page.columns().get(1).stream().map(TakaEntry::getTakaNo).toList());
        assertEquals(1190d, page.columns().get(0).stream().mapToDouble(TakaEntry::getMeters).sum());
    }

    @Test
    void usesAnotherChallanWhenMeterBalancingNeedsMoreThanFourColumns() {
        List<TakaEntry> takas = List.of(
                taka(1, 800), taka(2, 800), taka(3, 800), taka(4, 800), taka(5, 800),
                taka(6, 800), taka(7, 800), taka(8, 800), taka(9, 800), taka(10, 800));

        assertEquals(2, ChallanLayoutPlanner.pageCount(takas, true));
    }

    private TakaEntry taka(int takaNo, double meters) {
        TakaEntry taka = new TakaEntry();
        taka.setTakaNo(takaNo);
        taka.setMeters(meters);
        return taka;
    }
}
