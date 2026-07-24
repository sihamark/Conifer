package eu.heha.conifer

import kotlinx.serialization.json.Json

/**
 * Shared JSON codec for the sync wire format and the Login Flow v2 API responses. `Json`
 * instances are immutable and thread-safe once built, so one process-wide instance is both
 * simpler and avoids rebuilding the same config repeatedly.
 */
val AppJson = Json { ignoreUnknownKeys = true }
