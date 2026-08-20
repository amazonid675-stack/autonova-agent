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
import im.autonova.mobile.data.AgentRepository
import im.autonova.mobile.data.SecureConfig
import java.util.concurrent.TimeUnit

/** Replays only an authenticated, user-configured read synchronization when network is available. */
class AgentSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val config = SecureConfig(applicationContext)
        if (!config.isConfigured()) return Result.success()
        return if (AgentRepository(applicationContext, AgentDatabase.create(applicationContext).cacheDao(), config).refresh()) Result.success() else Result.retry()
    }
    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<AgentSyncWorker>().setConstraints(constraints).build()
            val periodic = PeriodicWorkRequestBuilder<AgentSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork("autonova-sync", ExistingWorkPolicy.KEEP, request)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("autonova-periodic-sync", ExistingPeriodicWorkPolicy.KEEP, periodic)
        }
    }
}
