package ai.byak.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ai.byak.app.agent.DeepResearchWorker
import ai.byak.app.data.local.dao.AgentDao
import ai.byak.app.data.local.entity.AgentRunEntity
import ai.byak.app.data.local.entity.AgentStepEntity
import ai.byak.app.domain.model.AgentRun
import ai.byak.app.domain.model.AgentStage
import ai.byak.app.domain.model.AgentStatus
import ai.byak.app.domain.model.AgentStep
import ai.byak.app.domain.model.AgentType
import ai.byak.app.domain.model.StepStatus
import ai.byak.app.domain.repository.AgentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AgentRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: AgentDao,
) : AgentRepository {
    private val workManager = WorkManager.getInstance(context)

    override fun observeRuns(): Flow<List<AgentRun>> = dao.observeRuns().map { items -> items.map(AgentRunEntity::toDomain) }
    override fun observeRun(runId: String): Flow<AgentRun?> = dao.observeRun(runId).map { it?.toDomain() }
    override fun observeSteps(runId: String): Flow<List<AgentStep>> = dao.observeSteps(runId).map { items -> items.map(AgentStepEntity::toDomain) }

    override suspend fun enqueue(type: AgentType, goal: String): String {
        val cleanGoal = goal.trim()
        require(cleanGoal.length >= 8) { "Describe the outcome in a little more detail" }
        val runId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertRun(AgentRunEntity(runId, type.name, cleanGoal, AgentStatus.QUEUED.name, createdAt = now, updatedAt = now))
        dao.upsertSteps(
            AgentStage.entries.mapIndexed { index, stage ->
                AgentStepEntity(
                    id = "$runId-${stage.name.lowercase()}",
                    runId = runId,
                    stage = stage.name,
                    title = stage.title(),
                    status = StepStatus.PENDING.name,
                    sequence = index,
                )
            },
        )
        val request = OneTimeWorkRequestBuilder<DeepResearchWorker>()
            .setInputData(Data.Builder().putString(DeepResearchWorker.KEY_RUN_ID, runId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .addTag("byak-agent")
            .addTag("byak-agent-$runId")
            .build()
        workManager.enqueueUniqueWork("byak-agent-$runId", ExistingWorkPolicy.KEEP, request)
        return runId
    }

    override suspend fun cancel(runId: String) {
        workManager.cancelUniqueWork("byak-agent-$runId")
        dao.updateRun(runId, AgentStatus.CANCELLED.name, 0, "", "Cancelled by user", System.currentTimeMillis())
    }
}

private fun AgentRunEntity.toDomain() = AgentRun(
    id, AgentType.valueOf(type), goal, AgentStatus.valueOf(status), progress, result, error, createdAt,
)

private fun AgentStepEntity.toDomain() = AgentStep(
    id, runId, AgentStage.valueOf(stage), title, detail, StepStatus.valueOf(status), sequence,
)

private fun AgentStage.title(): String = when (this) {
    AgentStage.PLAN -> "Plan the work"
    AgentStage.RESEARCH -> "Research and test assumptions"
    AgentStage.SYNTHESIZE -> "Synthesize the evidence"
    AgentStage.RESULT -> "Deliver the result"
}
