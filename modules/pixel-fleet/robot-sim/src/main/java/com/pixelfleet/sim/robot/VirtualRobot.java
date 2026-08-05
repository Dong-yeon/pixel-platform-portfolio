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
    private String currentOrderCode;
    private boolean chargingIntent;
    /** 지금 달리고 있(었)는 스텝. -1 = 주문 없음. */
    private int currentStepIndex = -1;
    /**
     * 적재 여부 — 스텝의 forLoad/forUnload를 <b>완료 시점에</b> 반영한 결과.
     * 예전엔 "픽업을 지났는가"로 추론했는데, 그건 스텝이 2개일 때만 맞는 우연이었다.
     */
    private boolean laden;
    /** 지금 레그의 목적지에서 싣는가/내리는가 — 도착했을 때 laden에 반영한다. */
    private boolean forLoadAtTarget;
    private boolean forUnloadAtTarget;

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
     * 스텝 하나(레그)를 받는다 — 첫 레그든 이어지는 레그든 같은 API다.
     *
     * <p>서버는 레그 단위로만 경로를 내준다: 주문 전체를 통째로 예약하면 먼 로봇이
     * 공장을 가로지르는 구간을 한 번에 요구해 다른 로봇이 거의 못 움직인다.
     * 경로(웨이포인트)는 통로를 따라 꺾어 가므로 중간 지점이 여럿이다.
     */
    public void assignLeg(String orderCode, int stepIndex, boolean forLoad, boolean forUnload,
                          List<double[]> waypoints) {
        path.clear();
        path.addAll(waypoints);
        this.currentOrderCode = orderCode;
        this.currentStepIndex = stepIndex;
        this.forLoadAtTarget = forLoad;
        this.forUnloadAtTarget = forUnload;
        this.chargingIntent = false;
        this.state = RobotState.MOVING;
    }

    /** 스텝을 마치고 다음 레그(또는 ORDER_DONE)를 기다리는 중인지. */
    public boolean isAwaitingNextLeg() {
        return currentOrderCode != null && path.isEmpty();
    }

    /** 레그 목적지 도착 — 스텝의 싣기/내리기를 적재 상태에 반영한다. */
    public void completeLeg() {
        if (forLoadAtTarget) {
            this.laden = true;
        }
        if (forUnloadAtTarget) {
            this.laden = false;
        }
    }

    public boolean isLaden() {
        return laden;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void startRoam(List<double[]> route) {
        path.clear();
        path.addAll(route);
        this.currentOrderCode = null;
        this.currentStepIndex = -1;
        this.chargingIntent = false;
        this.state = RobotState.MOVING;
    }

    public void startChargeRun(List<double[]> route) {
        path.clear();
        path.addAll(route);
        this.currentOrderCode = null;
        this.currentStepIndex = -1;
        this.chargingIntent = true;
        this.state = RobotState.MOVING;
    }

    /** 주문에서 손을 뗀다(ORDER_DONE·실패·중단). 실은 짐도 내려놓은 것으로 본다. */
    public void clearOrder() {
        path.clear();
        this.currentOrderCode = null;
        this.currentStepIndex = -1;
        this.chargingIntent = false;
        this.forLoadAtTarget = false;
        this.forUnloadAtTarget = false;
        this.laden = false;
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

    public boolean hasOrder() {
        return currentOrderCode != null;
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

    public String getCurrentOrderCode() {
        return currentOrderCode;
    }
}
