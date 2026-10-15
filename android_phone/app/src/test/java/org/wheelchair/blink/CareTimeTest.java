package org.wheelchair.blink;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class CareTimeTest {
    private final ZoneId india=ZoneId.of("Asia/Kolkata");
    private long at(String local){return LocalDateTime.parse(local).atZone(india).toInstant().toEpochMilli();}
    @Test public void dailyTimeSkipsAlreadyDueMinute(){assertEquals(at("2026-10-15T08:15:00"),CareTime.next(at("2026-10-14T08:15:00"),india,8,15,127,""));}
    @Test public void futureTodayIsNotMovedToTomorrow(){assertEquals(at("2026-10-14T08:15:00"),CareTime.next(at("2026-10-14T08:14:59"),india,8,15,127,""));}
    @Test public void mondayMaskRollsAcrossWeek(){assertEquals(at("2026-10-19T09:00:00"),CareTime.next(at("2026-10-14T08:00:00"),india,9,0,1,""));}
    @Test public void singleDateNeverRepeats(){long due=at("2026-10-14T09:00:00");assertEquals(due,CareTime.next(due-1,india,9,0,0,"2026-10-14"));assertEquals(-1,CareTime.next(due,india,9,0,127,"2026-10-14"));}
    @Test public void missingDaysDoesNotInventSchedule(){assertEquals(-1,CareTime.next(at("2026-10-14T08:00:00"),india,9,0,0,""));}
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidTime(){CareTime.next(0,india,24,0,127,"");}
    @Test public void springGapUsesNextValidLocalTime(){ZoneId zone=ZoneId.of("America/New_York");long now=LocalDateTime.parse("2026-03-08T00:00:00").atZone(zone).toInstant().toEpochMilli();assertEquals(LocalDateTime.parse("2026-03-08T03:30:00").atZone(zone).toInstant().toEpochMilli(),CareTime.next(now,zone,2,30,127,""));}
    @Test public void autumnRepeatedHourDoesNotRepeatDose(){ZoneId zone=ZoneId.of("America/New_York");long first=LocalDateTime.parse("2026-11-01T01:30:00").atZone(zone).withEarlierOffsetAtOverlap().toInstant().toEpochMilli();long next=CareTime.next(first,zone,1,30,127,"");assertEquals(LocalDate.of(2026,11,2),Instant.ofEpochMilli(next).atZone(zone).toLocalDate());}
    @Test public void latenessDoesNotAllowEarlyOrStalePlayback(){assertFalse(CareTime.timely(999,1000));assertTrue(CareTime.timely(1000,1000));assertTrue(CareTime.timely(121000,1000));assertFalse(CareTime.timely(121001,1000));}
    @Test public void duplicatesMatchButSnoozeGetsNewPlaybackId(){String id=CareTime.occurrenceId(7,1000);assertEquals(id,CareTime.occurrenceId(7,1000));assertNotEquals(id,CareTime.occurrenceId(8,1000));assertEquals(CareTime.playbackId(id,1000),CareTime.playbackId(id,1000));assertNotEquals(CareTime.playbackId(id,1000),CareTime.playbackId(id,601000));}
    @Test public void completedAndUnknownActionsAreFinal(){assertTrue(CareTime.actionable("PENDING"));assertTrue(CareTime.actionable("SNOOZED"));assertFalse(CareTime.actionable("DONE"));assertFalse(CareTime.actionable("SKIPPED"));assertFalse(CareTime.actionable(null));}
}
