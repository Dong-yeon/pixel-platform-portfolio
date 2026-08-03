package com.pixelfleet.sim.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sim")
public class SimProperties {

    private String brokerUrl;
    private String clientId;
    private long tickIntervalMs = 1000;
    private double speed = 1.5;
    private double batteryDrainPerTick = 0.4;
    private double chargePerTick = 2.0;
    // 관제 서버의 배차 최소 배터리(25%)보다 높아야 한다. 자세한 이유는 application.yml 주석.
    private int lowBatteryThreshold = 30;
    private double failureRate = 0.02;
    private boolean roam = true;
    private List<RobotDef> robots = new ArrayList<>();

    public static class RobotDef {
        private String code;
        private String name;
        private String home = "DOCK-1";
        /**
         * 이 로봇이 사는 층. <b>관제 서버 robots.floor_no 와 같아야 한다</b> —
         * 다르면 배차는 그 층으로 가는데 로봇은 딴 층 좌표에 서 있게 된다.
         * 층이 다른 로봇은 좌표가 겹쳐도 서로 겹친 것이 아니다(위아래로 떨어져 있다).
         */
        private int floor = 1;

        public String getCode() {
            return code;
        }

        public int getFloor() {
            return floor;
        }

        public void setFloor(int floor) {
            this.floor = floor;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHome() {
            return home;
        }

        public void setHome(String home) {
            this.home = home;
        }
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public long getTickIntervalMs() {
        return tickIntervalMs;
    }

    public void setTickIntervalMs(long tickIntervalMs) {
        this.tickIntervalMs = tickIntervalMs;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getBatteryDrainPerTick() {
        return batteryDrainPerTick;
    }

    public void setBatteryDrainPerTick(double batteryDrainPerTick) {
        this.batteryDrainPerTick = batteryDrainPerTick;
    }

    public double getChargePerTick() {
        return chargePerTick;
    }

    public void setChargePerTick(double chargePerTick) {
        this.chargePerTick = chargePerTick;
    }

    public int getLowBatteryThreshold() {
        return lowBatteryThreshold;
    }

    public void setLowBatteryThreshold(int lowBatteryThreshold) {
        this.lowBatteryThreshold = lowBatteryThreshold;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public boolean isRoam() {
        return roam;
    }

    public void setRoam(boolean roam) {
        this.roam = roam;
    }

    public List<RobotDef> getRobots() {
        return robots;
    }

    public void setRobots(List<RobotDef> robots) {
        this.robots = robots;
    }
}
