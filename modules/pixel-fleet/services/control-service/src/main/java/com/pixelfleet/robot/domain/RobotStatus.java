package com.pixelfleet.robot.domain;

public enum RobotStatus {
    IDLE,       // available for assignment
    MOVING,     // executing a transport task
    CHARGING,   // docked at a charging station
    ERROR,      // fault reported, needs attention
    OFFLINE     // no heartbeat / disconnected
}
