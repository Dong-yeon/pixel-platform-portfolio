package com.pixelfleet.sim.robot;

/**
 * Mirrors the control server's RobotStatus values (the MQTT status contract).
 */
public enum RobotState {
    OFFLINE,
    IDLE,
    MOVING,
    CHARGING,
    ERROR
}
