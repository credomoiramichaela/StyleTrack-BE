package com.styletrack.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayMongoSignatureTest {

    private static final String SECRET = "whsk_test_secret";
    private static final String BODY = "{\"data\":{\"attributes\":{\"type\":\"payment.paid\"}}}";

    @Test
    void acceptsTheTestSignatureInTestMode() {
        String t = "1700000000";
        String header = "t=" + t + ",te=" + PayMongoSignature.sign(t + "." + BODY, SECRET) + ",li=";
        assertTrue(PayMongoSignature.verify(header, BODY, SECRET, false));
    }

    @Test
    void acceptsTheLiveSignatureInLiveModeOnly() {
        String t = "1700000000";
        String sig = PayMongoSignature.sign(t + "." + BODY, SECRET);
        String header = "t=" + t + ",te=,li=" + sig;
        assertTrue(PayMongoSignature.verify(header, BODY, SECRET, true));
        assertFalse(PayMongoSignature.verify(header, BODY, SECRET, false));
    }

    @Test
    void rejectsTamperedBodiesWrongSecretsAndMissingParts() {
        String t = "1700000000";
        String header = "t=" + t + ",te=" + PayMongoSignature.sign(t + "." + BODY, SECRET) + ",li=";
        assertFalse(PayMongoSignature.verify(header, BODY + " ", SECRET, false));
        assertFalse(PayMongoSignature.verify(header, BODY, "another-secret", false));
        assertFalse(PayMongoSignature.verify(header, BODY, "", false));
        assertFalse(PayMongoSignature.verify(null, BODY, SECRET, false));
        assertFalse(PayMongoSignature.verify("te=abc", BODY, SECRET, false));
        assertFalse(PayMongoSignature.verify("t=1700000000,te=,li=", BODY, SECRET, false));
        assertFalse(PayMongoSignature.verify("garbage", BODY, SECRET, false));
    }
}
