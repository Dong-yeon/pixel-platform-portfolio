package com.pixelfleet;

import com.pixelfleet.auth.jwt.JwtProperties;
import com.pixelfleet.mqtt.MqttProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, MqttProperties.class})
public class PixelFleetApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelFleetApplication.class, args);
    }
}
