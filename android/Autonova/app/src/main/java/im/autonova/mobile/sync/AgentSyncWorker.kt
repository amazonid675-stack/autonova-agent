package im.autonova.mobile.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import im.autonova.mobile.data.AgentDatabase
import im.autonova.mobile.data.AgentNotifier
import im.autonova.mobile.data.AgentRepository
import im.autonova.mobile.data.SecureConfig
import java.util.concurrent.TimeUnit

/** Replays only an authenticated, user-configured read synchronization when network is available. */
class AgentSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val config = SecureConfig(applicationContext)
        if (!config.isConfigured() || !config.backgroundSyncEnabled()) return Result.success()
        val cache = AgentDatabase.create(applicationContext).cacheDao()
        val repository = AgentRepository(applicationContext, cache, config)
        val completed = repository.refresh() && repository.prepareLearningCandidates()
        val now = System.currentTimeMillis()
        val title = if (completed) "Background review completed" else "Background review needs attention"
        val detail = if (completed) "Workspace refresh and optional review-candidate preparation completed under your selected constraints." else "Autonova will retry when Android allows the configured network and battery conditions."
        cache.upsertActivity(listOf(im.autonova.mobile.data.CachedActivity("background-$now", title, detail, "BACKGROUND_REVIEW")))
        repository.recordDeviceCapability("background.review", "Android WorkManager", detail, if (completed) "COMPLETED" else "FAILED")
        if (config.notificationsEnabled()) AgentNotifier(applicationContext).notifyBackgroundReview(completed, detail)
        return if (completed) Result.success() else Result.retry()
    }
    companion object {
        fun enqueue(context: Context) {
            val config = SecureConfig(context)
            if (!config.backgroundSyncEnabled()) { WorkManager.getInstance(context).cancelUniqueWork("autonova-sync"); WorkManager.getInstance(context).cancelUniqueWork("autonova-periodic-sync"); return }
            val network = if (config.backgroundRequiresUnmeteredNetwork()) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder().setRequiredNetworkType(network).setRequiresCharging(config.backgroundRequiresCharging()).build()
            val request = OneTimeWorkRequestBuilder<AgentSyncWorker>().setConstraints(constraints).build()
            val periodic = PeriodicWorkRequestBuilder<AgentSyncWorker>(config.backgroundIntervalMinutes(), TimeUnit.MINUTES).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork("autonova-sync", ExistingWorkPolicy.KEEP, request)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("autonova-periodic-sync", ExistingPeriodicWorkPolicy.UPDATE, periodic)
        }
    }
}
