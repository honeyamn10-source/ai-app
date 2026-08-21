package ai.byak.app.domain.repository

import ai.byak.app.domain.model.AgentRun
import ai.byak.app.domain.model.AgentStep
import ai.byak.app.domain.model.AgentType
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    fun observeRuns(): Flow<List<AgentRun>>
    fun observeRun(runId: String): Flow<AgentRun?>
    fun observeSteps(runId: String): Flow<List<AgentStep>>
    suspend fun enqueue(type: AgentType, goal: String): String
    suspend fun cancel(runId: String)
}
