package com.miftah.core.model

enum class Outcome { SUCCEEDED, FAILED, REJECTED, TIMED_OUT, CANCELLED, UNSUPPORTED }
enum class RollbackAvailability { AVAILABLE, PARTIAL, UNAVAILABLE }

data class OperationResult(
    val operationId: String,
    val name: String,
    val timestampMillis: Long,
    val accessLevel: AccessLevel,
    val outcome: Outcome,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val previousState: String?,
    /** State read back from Android after the operation; null if it could not be read. */
    val verifiedState: String?,
    /** True only if an independent read of the system confirmed the target state. */
    val verified: Boolean,
    val rollback: RollbackAvailability,
    val rollbackNote: String?,
) {
    /** Success may only be displayed when the outcome is SUCCEEDED and independently verified. */
    val isConfirmedSuccess: Boolean get() = outcome == Outcome.SUCCEEDED && verified
}
