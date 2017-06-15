package com.easternedgerobotics.rov.io.devices;

/**
 * An Analog Digital Converter.
 */
public interface ADC {
    /**
     * Returns the voltage of this ADC.
     * @return the voltage of this ADC
     */
    float voltage();
}
