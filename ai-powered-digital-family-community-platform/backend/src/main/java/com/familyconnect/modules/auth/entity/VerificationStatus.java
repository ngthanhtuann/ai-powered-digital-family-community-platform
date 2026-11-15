package com.familyconnect.modules.auth.entity;

/** Trạng thái của member_verification. APPROVED là đã xác minh. */
public enum VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    REMOVED
}
