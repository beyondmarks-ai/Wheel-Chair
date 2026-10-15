package org.wheelchair.blink;

import java.time.*;

/** Wall-clock schedules follow the phone's time zone; missed times are never guessed into doses. */
final class CareTime {
    static long next(long now,ZoneId zone,int hour,int minute,int days,String once){
        if(hour<0||hour>23||minute<0||minute>59||days<0||days>127)throw new IllegalArgumentException();
        LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if(once!=null&&!once.isEmpty()){
            long due=LocalDate.parse(once).atTime(hour,minute).atZone(zone).toInstant().toEpochMilli();
            return due>now?due:-1;
        }
        if(days==0)return -1;
        for(int n=0;n<=7;n++){
            LocalDate date=today.plusDays(n);
            if((days&(1<<(date.getDayOfWeek().getValue()-1)))==0)continue;
            long due=date.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli();
            if(due>now)return due;
        }return -1;
    }
    static boolean timely(long now,long due){return now>=due&&now-due<=120000;}
    static String occurrenceId(long schedule,long due){return java.util.UUID.nameUUIDFromBytes((schedule+":"+due).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    static String playbackId(String occurrence,long notifyAt){return java.util.UUID.nameUUIDFromBytes((occurrence+":"+notifyAt).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    static boolean actionable(String state){return "PENDING".equals(state)||"SNOOZED".equals(state);}
}
