package com.pixelfleet.sim.robot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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

    /**
     * 경로를 웨이포인트 목록으로 받는다. 통로를 따라 꺾어 가는 경로라 중간 지점이 여럿이다
     * (NodeMap#route 참고) — 직선 한 번이 아니라 실제 주행처럼 움직인다.
     */
    public void assignTask(String taskCode, List<double[]> toOrigin, List<double[]> toDestination) {
        path.clear();
        path.addAll(toOrigin);
        path.addAll(toDestination);
        this.currentTaskCode = taskCode;
        this.chargingIntent = false;
        this.state = RobotState.MOVING;
    }

    public void startRoam(List<double[]> route) {
        path.clear();
        path.addAll(route);
        this.currentTaskCode = null;
        this.chargingIntent = false;
        this.state = RobotState.MOVING;
    }

    public void startChargeRun(List<double[]> route) {
        path.clear();
        path.addAll(route);
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

    /** 현재 위치. 경로를 계산할 때 출발점으로 쓴다. */
    public double[] position() {
        return new double[]{x, y};
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
