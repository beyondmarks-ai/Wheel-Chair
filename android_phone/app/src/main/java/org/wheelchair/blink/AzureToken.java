package org.wheelchair.blink;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AzureToken {
    public static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
    public static String make(String host, String device, String key, long expiry) throws Exception {
        String resource = encode(host + "/devices/" + device);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal((resource + "\n" + expiry).getBytes(StandardCharsets.UTF_8)));
        return "SharedAccessSignature sr=" + resource + "&sig=" + encode(signature) + "&se=" + expiry;
    }
}
