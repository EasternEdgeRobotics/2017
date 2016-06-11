package com.easternedgerobotics.rov.io.joystick;

import com.easternedgerobotics.rov.value.MotionValue;

import rx.Observable;

public interface Joystick {
    Observable<Boolean> button(final int index);

    Observable<MotionValue> axes();
}