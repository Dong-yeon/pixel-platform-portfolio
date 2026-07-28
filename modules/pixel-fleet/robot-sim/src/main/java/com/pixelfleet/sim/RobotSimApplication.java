package com.pixelfleet.sim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.sim.config.SimProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Fake robot fleet. Publishes status/position/battery/task telemetry over MQTT per
 * docs/mqtt-topics.md, and executes GOTO commands the control server sends on dispatch.
 * A stand-in for real ROS 2 robots until Phase 4 swaps this for a Gazebo/Nav2 bridge.
 */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(SimProperties.class)
public class RobotSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobotSimApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
