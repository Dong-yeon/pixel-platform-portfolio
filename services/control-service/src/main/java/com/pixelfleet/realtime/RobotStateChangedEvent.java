package com.pixelfleet.realtime;

import com.pixelfleet.robot.dto.RobotResponse;

/**
 * Internal application event: a robot's live state changed (status/position/battery).
 * Published inside the mutating transaction; broadcast to dashboards after it commits.
 * Carries an immutable snapshot so the post-commit listener needs no re-read.
 */
public record RobotStateChangedEvent(RobotResponse robot) {
}
