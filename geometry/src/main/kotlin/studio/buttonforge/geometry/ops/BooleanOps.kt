package studio.buttonforge.geometry.ops

import studio.buttonforge.geometry.path.CompoundPath

enum class BooleanOp { Union, Subtract, Intersect, Exclude }

data class BooleanOptions(
    val tolerance: Double = 1e-6,
    val isCancelled: () -> Boolean = { false },
)

enum class BooleanFailure { Unsupported, Degenerate, Cancelled, Internal }

sealed interface BooleanResult {
    data class Success(val path: CompoundPath, val notes: List<String> = emptyList()) : BooleanResult
    data class Failure(val reason: BooleanFailure, val message: String = "") : BooleanResult
}

/** Synchronous and pure. Implementations live outside this module (see PROJECT_STRUCTURE.md §4). */
interface BooleanOps {
    fun apply(op: BooleanOp, a: CompoundPath, b: CompoundPath, options: BooleanOptions = BooleanOptions()): BooleanResult
}
