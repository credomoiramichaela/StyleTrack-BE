package com.styletrack.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public class NumberUtil {

    public BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    public long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
