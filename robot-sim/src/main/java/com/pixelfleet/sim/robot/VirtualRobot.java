package com.pixelfleet.sim.robot;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mutable state and 2D kinematics of one simulated robot. Holds no MQTT/decision logic;
 * the {@code Simulator} drives it and decides what telemetry to publish.
 */
public class VirtualRobot {

    private final String code;
    private final String name;

    private double x;
    private double y;
    private double battery = 100.0;
    private RobotState state = RobotState.OFFLINE;

    private final Deque<double[]> path = new ArrayDeque<>();
    private String currentTaskCode;
    private boolean chargingIntent;

    public VirtualRobot(String code, String name, double x, double y) {
        this.code = code;
        this.name = name;
        this.x = x;
        this.y = y;
    }

    /** Move toward the head of the path by {@code speed}. Returns true if that point was reached. */
    public boolean advanceToward(double speed) {
        double[] target = path.peek();
        if (target == null) {
            return false;
        }
        double dx = target[0] - x;
        double dy = target[1] - y;
        double dist = Math.hypot(dx, dy);
        if (dist <= speed || dist == 0.0) {
            x = target[0];
            y = target[1];
            path.poll();
            return true;
        }
        x += dx / dist * speed;
        y += dy / dist * speed;
        return false;
    }

    public void assignTask(String taskCode, double[] origin, double[] destination) {
        path.clear();
        path.add(origin);
        path.add(destination);
        this.currentTaskCode = taskCode;
        this.chargingIntent = false;
        this.state = RobotState.MOVING;
    }

    public void startRoam(double[] destination) {
        path.clear();
        path.add(destination);
        this.currentTaskCode = null;
        this.chargingIntent = false;
        this.state = RobotState.MOVING;
    }

    public void startChargeRun(double[] dock) {
        path.clear();
        path.add(dock);
        this.currentTaskCode = null;
        this.chargingIntent = true;
        this.state = RobotState.MOVING;
    }

    public void abortTask() {
        path.clear();
        this.currentTaskCode = null;
        this.chargingIntent = false;
    }

    public void drain(double amount) {
        this.battery = Math.max(0.0, battery - amount);
    }

    public void charge(double amount) {
        this.battery = Math.min(100.0, battery + amount);
    }

    public boolean hasPath() {
        return !path.isEmpty();
    }

    public boolean hasTask() {
        return currentTaskCode != null;
    }

    public boolean isChargingIntent() {
        return chargingIntent;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getBattery() {
        return battery;
    }

    public int getBatteryPercent() {
        return (int) Math.round(battery);
    }

    public RobotState getState() {
        return state;
    }

    public void setState(RobotState state) {
        this.state = state;
    }

    public String getCurrentTaskCode() {
        return currentTaskCode;
    }
}
