package com.rioagent.app;

import java.util.List;

public final class DecisionEngine {
    public static final class Command {
        public final float steering;
        public final float throttle;
        public final float targetX, targetY;
        public final boolean hasTarget;

        Command(float steering, float throttle,
                float targetX, float targetY, boolean hasTarget) {
            this.steering = steering;
            this.throttle = throttle;
            this.targetX = targetX;
            this.targetY = targetY;
            this.hasTarget = hasTarget;
        }
    }

    private float previousSteering;
    private float targetX = -1f, targetY = -1f;

    public synchronized Command decide(List<RedMarkerDetector.Marker> markers) {
        if (markers == null || markers.isEmpty()) {
            targetX = targetY = -1f;
            previousSteering *= 0.7f;
            return new Command(previousSteering, 0f, -1f, -1f, false);
        }

        float tx = AgentConfig.tractorX;
        float ty = AgentConfig.tractorY;

        if (targetX >= 0f) {
            float distance = distance(targetX, targetY, tx, ty);
            if (distance <= AgentConfig.arrivalRadius) {
                targetX = targetY = -1f;
            }
        }

        RedMarkerDetector.Marker best = null;
        float bestScore = Float.MAX_VALUE;

        for (RedMarkerDetector.Marker marker : markers) {
            float dx = marker.x - tx;
            float dy = marker.y - ty;

            // Prefer markers ahead of the tractor; strongly penalize markers behind it.
            float score = (float)Math.sqrt(dx * dx + dy * dy);
            if (dy > 0.04f) score += 0.8f + dy;

            if (targetX >= 0f) {
                float continuity = distance(marker.x, marker.y, targetX, targetY);
                score += continuity * 0.35f;
            }

            if (score < bestScore) {
                bestScore = score;
                best = marker;
            }
        }

        if (best == null) {
            previousSteering *= 0.7f;
            return new Command(previousSteering, 0f, -1f, -1f, false);
        }

        targetX = best.x;
        targetY = best.y;

        float dx = targetX - tx;
        float dy = ty - targetY;
        float raw = dx / Math.max(0.12f, Math.abs(dy) + 0.12f);
        raw = AgentConfig.clamp(
                raw * AgentConfig.steeringSensitivity, -1f, 1f);

        float smooth = AgentConfig.clamp(
                AgentConfig.steeringSmoothing, 0f, 0.95f);
        previousSteering =
                previousSteering * smooth + raw * (1f - smooth);

        float speed = Math.abs(previousSteering) > 0.75f ? 0.35f : 0.65f;
        return new Command(previousSteering, speed, targetX, targetY, true);
    }

    public synchronized void reset() {
        previousSteering = 0f;
        targetX = targetY = -1f;
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }
}
