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
    private int lowBatteryThreshold = 20;
    private double failureRate = 0.02;
    private boolean roam = true;
    private List<RobotDef> robots = new ArrayList<>();

    public static class RobotDef {
        private String code;
        private String name;
        private String home = "DOCK-1";

        public String getCode() {
            return code;
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
