package com.miftah.core.execution

import com.miftah.core.model.OperationResult
import com.miftah.core.model.Outcome

data class PlanStep(val id: Int, val target: String, val command: ShellCommand, val verification: Verification?)

enum class StepStatus { PENDING, CONFIRMED, UNVERIFIED, FAILED, REJECTED, SKIPPED }
enum class StopReason { STEP_FAILED, STEP_UNVERIFIED, APPROVAL_DECLINED, DISCONNECTED }

data class StepOutcome(val step: PlanStep, val status: StepStatus, val result: OperationResult?)
data class PlanRunResult(val outcomes: List<StepOutcome>, val stoppedReason: StopReason?)

/**
 * Runs steps in order. Every destructive step needs its own approval, the run stops at the first step that
 * fails or cannot be verified, and it stops if the connection is lost.
 */
class PlanRunner(private val engine: ExecutionEngine, private val isConnected: () -> Boolean) {

    suspend fun run(
        steps: List<PlanStep>,
        approve: suspend (step: PlanStep, index: Int, total: Int) -> Boolean,
        onResult: suspend (PlanStep, OperationResult) -> Unit = { _, _ -> },
        onProgress: (List<StepOutcome>) -> Unit = {},
    ): PlanRunResult {
        val outcomes = steps.map { StepOutcome(it, StepStatus.PENDING, null) }.toMutableList()
        var stop: StopReason? = null
        for ((i, step) in steps.withIndex()) {
            if (stop != null) { outcomes[i] = StepOutcome(step, StepStatus.SKIPPED, null); continue }
            if (!isConnected()) {
                stop = StopReason.DISCONNECTED
                outcomes[i] = StepOutcome(step, StepStatus.SKIPPED, null)
                onProgress(outcomes.toList())
                continue
            }
            var approved = false
            if (step.command.destructive) {
                approved = approve(step, i, steps.size)
                if (!approved) {
                    stop = StopReason.APPROVAL_DECLINED
                    outcomes[i] = StepOutcome(step, StepStatus.REJECTED, null)
                    onProgress(outcomes.toList())
                    continue
                }
            }
            val result = engine.run(step.command, confirmed = approved, verification = step.verification)
            onResult(step, result)
            val status = when {
                result.isConfirmedSuccess -> StepStatus.CONFIRMED
                result.outcome == Outcome.SUCCEEDED -> StepStatus.UNVERIFIED
                else -> StepStatus.FAILED
            }
            outcomes[i] = StepOutcome(step, status, result)
            if (status == StepStatus.UNVERIFIED) stop = StopReason.STEP_UNVERIFIED
            if (status == StepStatus.FAILED) stop = StopReason.STEP_FAILED
            onProgress(outcomes.toList())
        }
        return PlanRunResult(outcomes, stop)
    }
}
