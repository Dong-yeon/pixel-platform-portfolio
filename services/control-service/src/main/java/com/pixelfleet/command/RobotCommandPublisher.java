package com.pixelfleet.command;

/**
 * Downlink to the robots: how the control server issues movement commands.
 * Implemented by an MQTT adapter ({@code fleet/{robotCode}/command}); kept as an
 * interface so domain services depend on the contract, not the transport.
 */
public interface RobotCommandPublisher {

    /**
     * Tell a robot to execute a transport task: pick up at {@code origin}, drop at
     * {@code destination}. No-op when the downlink is disabled.
     */
    void sendGoto(String robotCode, String taskCode, String origin, String destination);
}
