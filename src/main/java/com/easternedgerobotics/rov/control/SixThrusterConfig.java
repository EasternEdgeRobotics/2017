package com.easternedgerobotics.rov.control;

import com.easternedgerobotics.rov.event.EventPublisher;
import com.easternedgerobotics.rov.value.MotionPowerValue;
import com.easternedgerobotics.rov.value.MotionValue;
import com.easternedgerobotics.rov.value.ThrusterValue;

import java.util.Arrays;
import java.util.Collections;
// import java.util.StringJoiner;

public class SixThrusterConfig {

    private ThrusterValue portAftThruster;

    private ThrusterValue starboardAftThruster;

    private ThrusterValue portForeThruster;

    private ThrusterValue starboardForeThruster;

    private ThrusterValue portVertThruster;

    private ThrusterValue starboardVertThruster;

    private MotionPowerValue motionPower = MotionPowerValue.zero();

    private MotionValue motion = MotionValue.zero();

    private EventPublisher eventPublisher;

    public SixThrusterConfig(final EventPublisher e,
                             final ThrusterValue portAft,
                             final ThrusterValue starboardAft,
                             final ThrusterValue portFore,
                             final ThrusterValue starboardFore,
                             final ThrusterValue portVert,
                             final ThrusterValue starboardVert
    ) {
        eventPublisher = e;

        portAftThruster = portAft;
        starboardAftThruster = starboardAft;
        portForeThruster = portFore;
        starboardForeThruster = starboardFore;
        portVertThruster = portVert;
        starboardVertThruster = starboardVert;

        eventPublisher.valuesOfType(ThrusterValue.class).subscribe(thrusterValue -> {
            if (thrusterValue.getName().equals(portAftThruster.getName())) {
                portAftThruster = thrusterValue;
            } else if (thrusterValue.getName().equals(starboardAftThruster.getName())) {
                starboardAftThruster = thrusterValue;
            } else if (thrusterValue.getName().equals(portForeThruster.getName())) {
                portForeThruster = thrusterValue;
            } else if (thrusterValue.getName().equals(starboardForeThruster.getName())) {
                starboardForeThruster = thrusterValue;
            } else if (thrusterValue.getName().equals(portVertThruster.getName())) {
                portVertThruster = thrusterValue;
            } else if (thrusterValue.getName().equals(starboardVertThruster.getName())) {
                starboardVertThruster = thrusterValue;
            }
        });

        eventPublisher.valuesOfType(MotionValue.class).subscribe(m -> motion = m);

        eventPublisher.valuesOfType(MotionPowerValue.class).subscribe(m -> motionPower = m);
    }

    public final void update() {

        float starboardFore = 0;
        float portFore = 0;
        float starboardAft = 0;
        float portAft = 0;
        float starboardVert = 0;
        float portVert = 0;

        // Included for debugging purposes
        // final StringJoiner sj1 = new StringJoiner(", ", "[ ", " ]")
        //     .add("surge: " + String.valueOf(motion.getSurge()))
        //     .add("sway: " + String.valueOf(motion.getSway()))
        //     .add("heave: " + String.valueOf(motion.getHeave()))
        //     .add("roll: " + String.valueOf(motion.getRoll()))
        //     .add("pitch: " + String.valueOf(motion.getPitch()))
        //     .add("yaw: " + String.valueOf(motion.getYaw()));
        // System.out.println(sj1.toString());
        
        final float surge = motion.getSurge() * motionPower.getSurge() * motionPower.getGlobal();
        final float heave = motion.getHeave() * motionPower.getHeave() * motionPower.getGlobal();
        final float pitch = motion.getPitch() * motionPower.getPitch() * motionPower.getGlobal();
        final float roll = motion.getRoll() * motionPower.getRoll() * motionPower.getGlobal();
        final float yaw = motion.getYaw() * motionPower.getYaw() * motionPower.getGlobal();
        final float sway = motion.getSway() * motionPower.getSway() * motionPower.getGlobal();

        /**
         * Assumes inputs use standard 3D cartesian coordinates
         *
         *    y
         *    ^
         *    |
         *    |
         *    O - - > x
         *  z
         *
         * Surge is positive forward
         * Sway is positive right
         * Heave is positive up
         * Yaw is positive counterclockwise about z-axis (i.e. left turn)
         * Roll is positive counterclockwise about y-axis (i.e. roll right)
         * Pitch is positive counterclockwise about x-axis (i.e. pitch up)
         **/

        // Ratio of T200 (max forward thrust)/(max reverse thrust)
        final float forwardThrustRatio = 1.182f;

        if (!(surge == 0 && sway == 0 && yaw == 0)) {
            starboardFore = -surge + sway - yaw; 
            portFore = -surge - sway + yaw;
            starboardAft = surge + sway + yaw;
            portAft = surge - sway - yaw;

            final float maxThrustHorizontal = Collections.max(Arrays.asList(
                Math.abs(starboardFore),
                Math.abs(starboardAft),
                Math.abs(portFore),
                Math.abs(portAft)
            ));

            final float maxInputMagHorizontal = Collections.max(Arrays.asList(
                Math.abs(surge),
                Math.abs(sway),
                Math.abs(yaw))
            );

            final float horizontalScalar = maxInputMagHorizontal / maxThrustHorizontal;
            starboardFore *= horizontalScalar;
            starboardAft *= horizontalScalar;
            portFore *= horizontalScalar;
            portAft *= horizontalScalar;
        }

        if (!(heave == 0 && roll == 0)) {
            starboardVert = heave - roll;
            portVert = heave + roll;

            final float maxThrustVeritical = Collections.max(Arrays.asList(
                Math.abs(starboardVert),
                Math.abs(portVert)
            ));


            final float maxInputMagVertical = Collections.max(Arrays.asList(
                Math.abs(starboardVert),
                Math.abs(portVert))
            );

            final float verticalScalar = maxInputMagVertical / maxThrustVeritical;
            starboardVert *= verticalScalar;
            portVert *= verticalScalar;
        }

        // Positive thrust reduction.
        // Corrects for T200's higher forward efficiency
        // If only one vert is spinning positive, divide that thruster output by the thrust ratio
        if ((starboardVert > 0 && portVert < 0) || (starboardVert < 0 && portVert > 0)) {
            if (starboardVert > 0) {
                starboardVert = starboardVert / forwardThrustRatio;
            } else if (portVert > 0) {
                portVert = portVert / forwardThrustRatio;
            }
        }

        // We take the negative value on thrusters with counter-rotating propellers (stbd)
        eventPublisher.emit(portAftThruster.setSpeed(portAft));
        eventPublisher.emit(starboardAftThruster.setSpeed(-starboardAft));
        eventPublisher.emit(portForeThruster.setSpeed(portFore));
        eventPublisher.emit(starboardForeThruster.setSpeed(-starboardFore));
        eventPublisher.emit(portVertThruster.setSpeed(portVert));
        eventPublisher.emit(starboardVertThruster.setSpeed(-starboardVert));

        
        // Included for debugging purposes
        // final StringJoiner sj2 = new StringJoiner(", ", "[ ", " ]")
        //            .add("portFore: " + String.valueOf(portFore))
        //            .add("starboardFore: " + String.valueOf(-starboardFore))
        //            .add("portAft: " + String.valueOf(portAft))
        //            .add("starboardAft: " + String.valueOf(-starboardAft))
        //            .add("starboardVert: " + String.valueOf(-starboardVert))
        //            .add("portVert: " + String.valueOf(portVert));
        // System.out.println(sj2.toString());
    }

    public final void updateZero() {
        eventPublisher.emit(portAftThruster.setSpeed(0));
        eventPublisher.emit(starboardAftThruster.setSpeed(0));
        eventPublisher.emit(portForeThruster.setSpeed(0));
        eventPublisher.emit(starboardForeThruster.setSpeed(0));
        eventPublisher.emit(portVertThruster.setSpeed(0));
        eventPublisher.emit(starboardVertThruster.setSpeed(0));
    }
}
