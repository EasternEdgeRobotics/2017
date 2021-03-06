package com.easternedgerobotics.rov.config;

public interface SliderConfig {
    byte globalPowerSliderAddress();

    byte heavePowerSliderAddress();

    byte swayPowerSliderAddress();

    byte surgePowerSliderAddress();

    byte pitchPowerSliderAddress();

    byte yawPowerSliderAddress();

    byte lightAPowerSliderAddress();

    byte lightBPowerSliderAddress();

    byte aftPowerSliderAddress();

    byte forePowerSliderAddress();
}
