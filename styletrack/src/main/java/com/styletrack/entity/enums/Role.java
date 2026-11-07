package com.styletrack.entity.enums;

public enum Role {
    /** Owner — full access to all system features and user/role management. */
    SUPER_ADMIN,
    /** Staff/Manager — administrative access to appointments and assigned admin functions. */
    ADMIN,
    STYLIST,
    /** A salon customer signed in from the mobile app. */
    CUSTOMER
}
