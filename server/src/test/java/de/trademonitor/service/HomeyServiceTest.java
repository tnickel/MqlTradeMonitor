package de.trademonitor.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class HomeyServiceTest {

    private final HomeyService homeyService = new HomeyService(null);

    @Test
    void testIsSirenMutedPeriod() {
        // Saturday morning
        LocalDateTime satMorning = LocalDateTime.of(2026, 6, 20, 8, 30);
        assertTrue(homeyService.isSirenMutedPeriod(satMorning));

        // Saturday night
        LocalDateTime satNight = LocalDateTime.of(2026, 6, 20, 23, 59);
        assertTrue(homeyService.isSirenMutedPeriod(satNight));

        // Sunday morning
        LocalDateTime sunMorning = LocalDateTime.of(2026, 6, 21, 9, 0);
        assertTrue(homeyService.isSirenMutedPeriod(sunMorning));

        // Sunday evening before 23:00 (e.g. 22:59)
        LocalDateTime sunEveningBefore = LocalDateTime.of(2026, 6, 21, 22, 59);
        assertTrue(homeyService.isSirenMutedPeriod(sunEveningBefore));

        // Sunday evening at 23:00
        LocalDateTime sunEveningAt = LocalDateTime.of(2026, 6, 21, 23, 0);
        assertFalse(homeyService.isSirenMutedPeriod(sunEveningAt));

        // Sunday evening after 23:00 (e.g. 23:30)
        LocalDateTime sunEveningAfter = LocalDateTime.of(2026, 6, 21, 23, 30);
        assertFalse(homeyService.isSirenMutedPeriod(sunEveningAfter));

        // Monday morning
        LocalDateTime monMorning = LocalDateTime.of(2026, 6, 22, 8, 0);
        assertFalse(homeyService.isSirenMutedPeriod(monMorning));

        // Friday afternoon
        LocalDateTime friAfternoon = LocalDateTime.of(2026, 6, 19, 15, 0);
        assertFalse(homeyService.isSirenMutedPeriod(friAfternoon));
    }
}
