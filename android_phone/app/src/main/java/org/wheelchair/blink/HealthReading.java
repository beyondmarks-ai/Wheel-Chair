package org.wheelchair.blink;

/** Versioned Bluetooth measurements; '-' means unavailable, never zero. */
record HealthReading(long uptime, Double bpm, Double oxygen, Double roomC, Double humidity,
                     Double distance, Boolean moving, boolean finger, long motionEvents) {
    static HealthReading parse(String line) {
        try {
            String[] p=line.split(" ");
            if(p.length!=11||!p[0].equals("HEALTH")||!p[1].equals("1"))return null;
            long uptime=Long.parseLong(p[2]);if(uptime<0||uptime>4294967295L)return null;
            var r=new HealthReading(uptime,value(p[3],21,239),value(p[4],1,100),value(p[5],-40,85),
                    value(p[6],0,100),value(p[7],2,450),flag(p[8],true),flag(p[9],false),Long.parseLong(p[10]));
            if(r.motionEvents<0||r.motionEvents>4294967295L)return null;
            if(!r.finger&&(r.bpm!=null||r.oxygen!=null))return null;
            return r;
        }catch(RuntimeException bad){return null;}
    }
    private static Double value(String text,double min,double max) {
        if(text.equals("-"))return null;
        double value=Double.parseDouble(text);
        if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException();
        return value;
    }
    private static Boolean flag(String text,boolean nullable) {
        if(nullable&&text.equals("-"))return null;
        if(text.equals("0"))return false;if(text.equals("1"))return true;
        throw new IllegalArgumentException();
    }
    Double metric(int index){return switch(index){case 0->bpm;case 1->oxygen;case 2->roomC;case 3->humidity;default->distance;};}
}
