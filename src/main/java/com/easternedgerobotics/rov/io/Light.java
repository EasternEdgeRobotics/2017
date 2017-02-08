package com.easternedgerobotics.rov.io;

import com.easternedgerobotics.rov.value.SpeedValue;

public class Light {
    /**
     * Max forward PWM signal value (in μs) for the connected to lighting controller.
     */
    public static final float MAX_FWD = 2000;

    /**
     * Max reverse used to center the stop period at 0%.
     */
    public static final float MAX_REV = 1000;

    /**
     * Flashing period for the lights.
     */
    private static final float PERIOD = 600;

    /**
     * Flashing max intensity for the lights.
     */
    private static final float AMPLITUDE = .3f;

    /**
     * The output PWM device to write speeds.
     */
    private final PWM device;

    /**
     * Create a Lighting device which uses a pololu channel for communication.
     * The Min and Max SpeedValue value for this device is 0 and 1 respectively.
     *
     * @param device PWM output to write speeds
     */
    public Light(final PWM device) {
        this.device = device;
    }

    /**
     * Write the latest {@code SpeedValue} for this light.
     */
    public final void write(final SpeedValue value) {
        if (value.getSpeed() < 0 || value.getSpeed() > 1) {
            throw new IllegalArgumentException("Light channel values must be between 0 and 1");
        }
        device.write(value.getSpeed());
    }

    /**
     * Write the default flashing light values.
     */
    public final void flash() {
        device.write((float) (AMPLITUDE * (1 - Math.sin(2 * Math.PI / PERIOD * System.currentTimeMillis()))));
    }
}
