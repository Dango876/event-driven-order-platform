package com.procurehub.order.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public final class TestJwtFactory {

    private static final String JWT_SECRET =
            "VGhpc0lzRGV2T25seVN1cGVyU2VjcmV0S2V5VGhhdE11c3RCZUNoYW5nZWRJblByb2QxMjM0NQ==";

    private TestJwtFactory() {
    }

    public static String userToken() {
        return token("test-user@example.com", "ROLE_USER");
    }

    public static String adminToken() {
        return token("test-admin@example.com", "ROLE_ADMIN");
    }

    private static String token(String subject, String role) {
        long now = Instant.now().getEpochSecond();

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = "{\"roles\":[\"" + role + "\"],\"sub\":\"" + subject + "\",\"iat\":" + now
                + ",\"exp\":" + (now + 3600) + "}";

        String header = base64Url(headerJson);
        String payload = base64Url(payloadJson);
        String signature = sign(header + "." + payload);

        return header + "." + payload + "." + signature;
    }

    private static String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] keyBytes = Base64.getDecoder().decode(JWT_SECRET);
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build test JWT", ex);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
