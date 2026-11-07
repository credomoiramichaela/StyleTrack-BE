package com.styletrack.entity.enums;

public enum OnlinePaymentStatus {
    /** Started; waiting for the customer to pay. */
    PENDING,
    PAID,
    FAILED,
    EXPIRED,
    /** Money arrived but can't be applied (the booking was already paid or got cancelled). The salon must refund it in PayMongo. */
    NEEDS_REFUND,
    /** The salon refunded a NEEDS_REFUND payment in PayMongo and cleared it here. */
    REFUNDED
}
