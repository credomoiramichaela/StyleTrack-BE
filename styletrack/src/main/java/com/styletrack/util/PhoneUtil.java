package com.styletrack.util;

import lombok.experimental.UtilityClass;

/** Mobile numbers are stored as digits only, with the Philippine +63 prefix written as a leading 0. */
@UtilityClass
public class PhoneUtil {

    public String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("63")) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }

    public boolean isValid(String normalized) {
        return normalized != null && normalized.length() >= 7 && normalized.length() <= 15;
    }
}
