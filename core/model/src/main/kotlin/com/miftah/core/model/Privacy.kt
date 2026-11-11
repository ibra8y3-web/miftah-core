package com.miftah.core.model

enum class PrivacyCategory { ACCESSIBILITY, NOTIFICATION_LISTENER, DEVICE_ADMIN, OVERLAY, USAGE_ACCESS, INSTALL_UNKNOWN }

data class PrivacyFinding(val category: PrivacyCategory, val pkg: String, val label: String, val isSystem: Boolean)

/** A sensitive runtime permission and the user-installed apps that currently hold it. */
data class SensitiveGrant(val permission: String, val packages: List<String>)

data class PrivacyReport(
    val findings: List<PrivacyFinding>,
    val sensitiveGrants: List<SensitiveGrant>,
    val scannedPackages: Int,
    /** Checks that Android refused to answer; shown instead of guessing. */
    val unreadable: Int,
    val readAtMillis: Long,
)

enum class RiskLevel { LOW, MEDIUM, HIGH }

/** Transparent scoring: each finding adds points; findings in system apps count a third. */
object PrivacyScore {
    val SENSITIVE_PERMISSIONS = listOf(
        "android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.READ_SMS",
        "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG",
    )

    fun points(category: PrivacyCategory): Int = when (category) {
        PrivacyCategory.ACCESSIBILITY, PrivacyCategory.DEVICE_ADMIN -> 5
        PrivacyCategory.NOTIFICATION_LISTENER, PrivacyCategory.INSTALL_UNKNOWN -> 3
        PrivacyCategory.OVERLAY, PrivacyCategory.USAGE_ACCESS -> 2
    }

    fun weight(f: PrivacyFinding): Int = if (f.isSystem) points(f.category) / 3 else points(f.category)

    fun score(findings: List<PrivacyFinding>): Int = findings.sumOf { weight(it) }

    fun level(score: Int): RiskLevel = when {
        score < 8 -> RiskLevel.LOW
        score < 20 -> RiskLevel.MEDIUM
        else -> RiskLevel.HIGH
    }
}
