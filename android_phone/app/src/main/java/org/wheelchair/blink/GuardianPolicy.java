package org.wheelchair.blink;

final class GuardianPolicy {
    static String number(String raw){
        if(raw==null)return null;
        String n=raw.replaceAll("[\\s()-]","");
        return n.matches("\\+[1-9][0-9]{7,14}")?n:null;
    }
    static boolean validId(String id){return id!=null&&id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");}
    static String message(String time,String id,boolean test){
        if(!validId(id)||!time.matches("[0-9]{2}:[0-9]{2} [0-9]{2}/[0-9]{2}"))throw new IllegalArgumentException();
        return test?"TEST ONLY: Blink Assist guardian SMS setup. No emergency. "+time:
                "Wheelchair prototype alert: three blinks requested help at "+time+". Please check on the user. Ref "+id.substring(0,8);
    }
    static String deliveryStatus(int report,String format){
        if(report==0)return "DELIVERED";
        if("3gpp".equals(format)&&report>=64&&report<=255)return "DELIVERY_FAILED";
        if("3gpp2".equals(format)&&((report>>>8)&3)==3)return "DELIVERY_FAILED";
        return null; // Pending, forwarded, malformed or unsupported reports are not delivery proof.
    }
}
