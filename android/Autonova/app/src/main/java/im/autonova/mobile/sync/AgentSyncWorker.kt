package im.autonova.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** WorkManager boundary for resumable, network-constrained sync; it performs no privileged action yet. */
class AgentSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) { override suspend fun doWork(): Result = Result.success() }
