package org.wheelchair.blink;

/** Only recordings exposed by the controller's acknowledged Bluetooth protocol. */
enum ManualRecording {
    EMERGENCY(1,"Emergency"), HELP(4,"Please help me"), COME(5,"Please come here"), YES(3,"Yes");
    final int track;
    final String phrase;
    ManualRecording(int track,String phrase){this.track=track;this.phrase=phrase;}
    static ManualRecording forTrack(int track){
        for(var recording:values())if(recording.track==track)return recording;
        return null;
    }
}
