package im.autonova.mobile.data

/** Persists the local audit trail before attempting the protected server-side consent-grant sync. */
data class DeviceAuditEvent(
    val capability: String,
    val scope: String,
    val detail: String,
    val outcome: String,
)

fun interface DeviceAuditRemote {
    suspend fun sync(event: DeviceAuditEvent)
}

class DeviceAuditSynchronizer(private val cache: AgentCacheDao) {
    suspend fun record(event: DeviceAuditEvent, remoteAudit: DeviceAuditRemote?): Boolean {
        val now = System.currentTimeMillis()
        val status = if (event.outcome == "REVOKED") "REVOKED" else "APPROVED"
        cache.upsertCapabilityGrants(listOf(CachedCapabilityGrant("local-grant-$now", event.capability, event.scope, event.detail, status, "", event.outcome)))
        cache.upsertActivity(listOf(CachedActivity("local-audit-$now", "${event.capability} ${event.outcome}", event.detail, "CAPABILITY_AUDIT")))
        return try { remoteAudit?.sync(event); true } catch (_: Exception) { false }
    }
}
