package org.wheelchair.blink;

enum CareSound {
    CUSTOM("custom","My own reminder text",0,0),
    TABLET("tablet","Tablet time",2990,R.raw.voice_tablet),
    MEDICINE("medicine","Medicine time",2991,R.raw.voice_medicine),
    DOCTOR("doctor","Doctor visit",2992,R.raw.voice_doctor),
    WAKE_UP("wake_up","Wake-up",2900,R.raw.voice_wake_up),
    MORNING_CARE("morning_care","Morning care",2901,R.raw.voice_morning_care),
    BREAKFAST("breakfast","Breakfast",2902,R.raw.voice_breakfast),
    WATER("water","Water break",2903,R.raw.voice_water),
    POSITION_CHANGE("position_change","Position change",2904,R.raw.voice_position_change),
    PHYSIOTHERAPY("physiotherapy","Physiotherapy",2905,R.raw.voice_physiotherapy),
    LUNCH("lunch","Lunch",2906,R.raw.voice_lunch),
    TOILET_ROUTINE("toilet_routine","Toilet routine",2907,R.raw.voice_toilet_routine),
    HEALTH_CHECK("health_check","Health check",2908,R.raw.voice_health_check),
    REST("rest","Rest break",2909,R.raw.voice_rest),
    DINNER("dinner","Dinner",2910,R.raw.voice_dinner),
    BEDTIME("bedtime","Bedtime",2911,R.raw.voice_bedtime);
    static final int FIRST_RESERVED_TRACK=2900;
    final String key,label;final int track,resource;
    CareSound(String key,String label,int track,int resource){this.key=key;this.label=label;this.track=track;this.resource=resource;}
    static CareSound from(String key){for(var sound:values())if(sound.key.equals(key))return sound;return CUSTOM;}
    static int customTrack(long id){return id>0&&id<FIRST_RESERVED_TRACK-100?(int)id+100:0;}
    String reminderKind(){
        return switch(this){case TABLET,MEDICINE->"Medicine";case DOCTOR->"Appointment";
            case BREAKFAST,LUNCH,DINNER->"Meal";default->"Activity";};
    }
}
