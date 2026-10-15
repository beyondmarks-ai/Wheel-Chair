package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuardianPolicyTest {
    private static final String ID="12345678-1234-1234-1234-123456789abc";
    @Test public void onlyExplicitInternationalPhoneNumbersAreAccepted(){
        assertEquals("+919876543210",GuardianPolicy.number("+91 (98765) 43210"));
        for(String input:new String[]{null,"","9876543210","+0001234567","+9198,12345678","+9198;12345678","+1234567890123456","+12","+91abc12345678"})assertNull(GuardianPolicy.number(input));
    }
    @Test public void alertMessagesFitOneSmsAndTestsAreClearlyLabelled(){
        String real=GuardianPolicy.message("23:59 31/12",ID,false),test=GuardianPolicy.message("23:59 31/12",ID,true);
        assertTrue(real.contains("three blinks"));assertTrue(real.contains("12345678"));
        assertTrue(test.startsWith("TEST ONLY"));assertTrue(test.contains("No emergency"));
        for(String message:new String[]{real,test}){
            assertTrue(message.length()<=160);assertTrue(message.chars().allMatch(c->c>=32&&c<=126));
        }
    }
    @Test public void malformedIdentifiersAndTimestampsCannotCreateMessages(){
        assertFalse(GuardianPolicy.validId("------------------------------------"));assertFalse(GuardianPolicy.validId("1-1-1-1-1"));
        assertThrows(IllegalArgumentException.class,()->GuardianPolicy.message("now",ID,false));
        assertThrows(IllegalArgumentException.class,()->GuardianPolicy.message("23:59 31/12","bad",false));
    }
    @Test public void pendingForwardedAndMissingCarrierReportsAreNotClaimedAsDelivered(){
        assertEquals("DELIVERED",GuardianPolicy.deliveryStatus(0,"3gpp"));
        assertEquals("DELIVERED",GuardianPolicy.deliveryStatus(0,"3gpp2"));
        assertEquals("DELIVERY_FAILED",GuardianPolicy.deliveryStatus(64,"3gpp"));
        assertEquals("DELIVERY_FAILED",GuardianPolicy.deliveryStatus(768,"3gpp2"));
        for(int code:new int[]{-1,1,32,63})assertNull(GuardianPolicy.deliveryStatus(code,"3gpp"));
        assertNull(GuardianPolicy.deliveryStatus(256,"3gpp2"));
    }
}
