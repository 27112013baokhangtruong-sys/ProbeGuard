package dev.probeguard.detect;

/**
 * A single finding. Two findings are "the same" when their type and detail
 * match, which is how the engine avoids counting the same signal twice.
 */
public record Violation(ViolationType type, String detail, int weight, long at) {
}
