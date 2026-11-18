package com.nitsutech.sleepremote;

import com.nitsutech.sleepremote.wear.WearProtocol;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WearProtocolTest {

    @Test
    public void toCalendarDays_mapsWeekdaysCorrectly() {
        int weekdays = WearProtocol.BIT_MON | WearProtocol.BIT_TUE | WearProtocol.BIT_WED
                | WearProtocol.BIT_THU | WearProtocol.BIT_FRI;
        ArrayList<Integer> days = WearProtocol.toCalendarDays(weekdays);
        assertEquals(5, days.size());
        assertTrue(days.contains(Calendar.MONDAY));
        assertTrue(days.contains(Calendar.FRIDAY));
    }

    @Test
    public void toCalendarDays_zeroReturnsEmpty() {
        ArrayList<Integer> days = WearProtocol.toCalendarDays(0);
        assertTrue(days.isEmpty());
    }

    @Test
    public void computeNextAlarmMillis_sortsTonightBeforeTomorrowMorning() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.NOVEMBER, 18, 17, 0, 0);
        now.set(Calendar.MILLISECOND, 0);
        long nowMs = now.getTimeInMillis();

        long tonight = WearProtocol.computeNextAlarmMillis(23, 55, 0, nowMs);
        int weekdays = WearProtocol.BIT_MON | WearProtocol.BIT_TUE | WearProtocol.BIT_WED
                | WearProtocol.BIT_THU | WearProtocol.BIT_FRI;
        long tomorrowMorning = WearProtocol.computeNextAlarmMillis(8, 45, weekdays, nowMs);
        int weekend = WearProtocol.BIT_SAT | WearProtocol.BIT_SUN;
        long saturday = WearProtocol.computeNextAlarmMillis(13, 30, weekend, nowMs);

        assertTrue(tonight < tomorrowMorning);
        assertTrue(tomorrowMorning < saturday);
    }
}
