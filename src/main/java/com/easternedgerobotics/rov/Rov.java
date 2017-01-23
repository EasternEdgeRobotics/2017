package com.easternedgerobotics.rov;

import com.easternedgerobotics.rov.control.SixThrusterConfig;
import com.easternedgerobotics.rov.event.BroadcastEventPublisher;
import com.easternedgerobotics.rov.event.EventPublisher;
import com.easternedgerobotics.rov.io.ADC;
import com.easternedgerobotics.rov.io.Accelerometer;
import com.easternedgerobotics.rov.io.Barometer;
import com.easternedgerobotics.rov.io.CpuInformation;
import com.easternedgerobotics.rov.io.CurrentSensor;
import com.easternedgerobotics.rov.io.Gyroscope;
import com.easternedgerobotics.rov.io.Light;
import com.easternedgerobotics.rov.io.Magnetometer;
import com.easternedgerobotics.rov.io.Motor;
import com.easternedgerobotics.rov.io.PWM;
import com.easternedgerobotics.rov.io.Thermometer;
import com.easternedgerobotics.rov.io.Thruster;
import com.easternedgerobotics.rov.io.VoltageSensor;
import com.easternedgerobotics.rov.io.pololu.AltIMU10v3;
import com.easternedgerobotics.rov.io.pololu.Maestro;
import com.easternedgerobotics.rov.math.Range;
import com.easternedgerobotics.rov.value.CameraSpeedValueA;
import com.easternedgerobotics.rov.value.CameraSpeedValueB;
import com.easternedgerobotics.rov.value.HeartbeatValue;
import com.easternedgerobotics.rov.value.LightSpeedValue;
import com.easternedgerobotics.rov.value.PortAftSpeedValue;
import com.easternedgerobotics.rov.value.PortForeSpeedValue;
import com.easternedgerobotics.rov.value.PortVertSpeedValue;
import com.easternedgerobotics.rov.value.SpeedValue;
import com.easternedgerobotics.rov.value.StarboardAftSpeedValue;
import com.easternedgerobotics.rov.value.StarboardForeSpeedValue;
import com.easternedgerobotics.rov.value.StarboardVertSpeedValue;
import com.easternedgerobotics.rov.value.ToolingSpeedValue;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.pmw.tinylog.Logger;
import rx.Observable;
import rx.Scheduler;
import rx.broadcast.BasicOrder;
import rx.broadcast.UdpBroadcast;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class Rov {
    static final long MAX_HEARTBEAT_GAP = 5;

    static final long CPU_POLL_INTERVAL = 1;

    static final long SENSOR_POLL_INTERVAL = 10;

    static final long SLEEP_DURATION = 100;

    static final byte MAESTRO_DEVICE_NUMBER = 0x01;

    static final byte PORT_AFT_CHANNEL = 15;

    static final byte STARBOARD_AFT_CHANNEL = 12;

    static final byte PORT_FORE_CHANNEL = 16;

    static final byte STARBOARD_FORE_CHANNEL = 13;

    static final byte PORT_VERT_CHANNEL = 17;

    static final byte STARBOARD_VERT_CHANNEL = 14;

    static final byte CAMERA_A_MOTOR_CHANNEL = 18;

    static final byte CAMERA_B_MOTOR_CHANNEL = 19;

    static final byte TOOLING_MOTOR_CHANNEL = 22;

    static final byte LIGHT_CHANNEL = 23;

    static final byte VOLTAGE_SENSOR_05V_CHANNEL = 8;

    static final byte VOLTAGE_SENSOR_12V_CHANNEL = 7;

    static final byte VOLTAGE_SENSOR_48V_CHANNEL = 6;

    static final byte CURRENT_SENSOR_05V_CHANNEL = 11;

    static final byte CURRENT_SENSOR_12V_CHANNEL = 10;

    static final byte CURRENT_SENSOR_48V_CHANNEL = 9;

    static final int ALT_IMU_I2C_BUS = I2CBus.BUS_1;

    static final boolean ALT_IMU_SA0_HIGH = false;

    private final SixThrusterConfig thrusterConfig;

    private final List<Thruster> thrusters;

    private final List<Motor> motors;

    private final List<Light> lights;

    private final EventPublisher eventPublisher;

    private final AtomicBoolean dead = new AtomicBoolean();

    private final Subject<Void, Void> killSwitch = PublishSubject.create();

    <AltIMU extends Accelerometer & Barometer & Thermometer & Gyroscope & Magnetometer,
            MaestroChannel extends ADC & PWM> Rov(
        final EventPublisher eventPublisher,
        final List<MaestroChannel> channels,
        final AltIMU imu
    ) {
        this.eventPublisher = eventPublisher;

        final PortAftSpeedValue portAft = new PortAftSpeedValue();
        final StarboardAftSpeedValue starboardAft = new StarboardAftSpeedValue();
        final PortForeSpeedValue portFore = new PortForeSpeedValue();
        final StarboardForeSpeedValue starboardFore = new StarboardForeSpeedValue();
        final PortVertSpeedValue portVert = new PortVertSpeedValue();
        final StarboardVertSpeedValue starboardVert = new StarboardVertSpeedValue();

        this.thrusterConfig = new SixThrusterConfig(eventPublisher);

        this.motors = Collections.unmodifiableList(Arrays.asList(
            new Motor(
                eventPublisher
                    .valuesOfType(CameraSpeedValueA.class)
                    .startWith(new CameraSpeedValueA())
                    .cast(SpeedValue.class),
                channels.get(CAMERA_A_MOTOR_CHANNEL).setOutputRange(new Range(Motor.MAX_REV, Motor.MAX_FWD))),
            new Motor(
                eventPublisher
                    .valuesOfType(CameraSpeedValueB.class)
                    .startWith(new CameraSpeedValueB())
                    .cast(SpeedValue.class),
                channels.get(CAMERA_B_MOTOR_CHANNEL).setOutputRange(new Range(Motor.MAX_REV, Motor.MAX_FWD))),
            new Motor(
                eventPublisher
                    .valuesOfType(ToolingSpeedValue.class)
                    .startWith(new ToolingSpeedValue())
                    .cast(SpeedValue.class),
                channels.get(TOOLING_MOTOR_CHANNEL).setOutputRange(new Range(Motor.MAX_REV, Motor.MAX_FWD)))
        ));

        this.thrusters = Collections.unmodifiableList(Arrays.asList(
            new Thruster(
                eventPublisher
                    .valuesOfType(PortAftSpeedValue.class)
                    .startWith(portAft)
                    .cast(SpeedValue.class),
                channels.get(PORT_AFT_CHANNEL).setOutputRange(new Range(Thruster.MAX_REV, Thruster.MAX_FWD))),
            new Thruster(
                eventPublisher
                    .valuesOfType(StarboardAftSpeedValue.class)
                    .startWith(starboardAft)
                    .cast(SpeedValue.class),
                channels.get(STARBOARD_AFT_CHANNEL).setOutputRange(new Range(Thruster.MAX_FWD, Thruster.MAX_REV))),
            new Thruster(
                eventPublisher
                    .valuesOfType(PortForeSpeedValue.class)
                    .startWith(portFore)
                    .cast(SpeedValue.class),
                channels.get(PORT_FORE_CHANNEL).setOutputRange(new Range(Thruster.MAX_REV, Thruster.MAX_FWD))),
            new Thruster(
                eventPublisher
                    .valuesOfType(StarboardForeSpeedValue.class)
                    .startWith(starboardFore)
                    .cast(SpeedValue.class),
                channels.get(STARBOARD_FORE_CHANNEL).setOutputRange(new Range(Thruster.MAX_FWD, Thruster.MAX_REV))),
            new Thruster(
                eventPublisher
                    .valuesOfType(PortVertSpeedValue.class)
                    .startWith(portVert)
                    .cast(SpeedValue.class),
                channels.get(PORT_VERT_CHANNEL).setOutputRange(new Range(Thruster.MAX_FWD, Thruster.MAX_REV))),
            new Thruster(
                eventPublisher
                    .valuesOfType(StarboardVertSpeedValue.class)
                    .startWith(starboardVert)
                    .cast(SpeedValue.class),
                channels.get(STARBOARD_VERT_CHANNEL).setOutputRange(new Range(Thruster.MAX_FWD, Thruster.MAX_REV)))
        ));

        this.lights = Collections.singletonList(
            new Light(
                eventPublisher
                    .valuesOfType(LightSpeedValue.class)
                    .startWith(new LightSpeedValue())
                    .cast(SpeedValue.class),
                channels.get(LIGHT_CHANNEL).setOutputRange(new Range(Light.MAX_REV, Light.MAX_FWD))
            )
        );

        final List<VoltageSensor> voltageSensors = Collections.unmodifiableList(Arrays.asList(
            VoltageSensor.V05.apply(channels.get(VOLTAGE_SENSOR_05V_CHANNEL)),
            VoltageSensor.V12.apply(channels.get(VOLTAGE_SENSOR_12V_CHANNEL)),
            VoltageSensor.V48.apply(channels.get(VOLTAGE_SENSOR_48V_CHANNEL))
        ));

        final List<CurrentSensor> currentSensors = Collections.unmodifiableList(Arrays.asList(
            CurrentSensor.V05.apply(channels.get(CURRENT_SENSOR_05V_CHANNEL)),
            CurrentSensor.V12.apply(channels.get(CURRENT_SENSOR_12V_CHANNEL)),
            CurrentSensor.V48.apply(channels.get(CURRENT_SENSOR_48V_CHANNEL))
        ));

        final Observable<Long> sensorInterval = Observable.interval(
                SENSOR_POLL_INTERVAL, TimeUnit.MILLISECONDS);

        sensorInterval.subscribe(tick -> {
            eventPublisher.emit(imu.pressure());
            eventPublisher.emit(imu.rotation());
            eventPublisher.emit(imu.acceleration());
            eventPublisher.emit(imu.angularVelocity());
            eventPublisher.emit(imu.temperature());
            voltageSensors.forEach(sensor -> eventPublisher.emit(sensor.read()));
            currentSensors.forEach(sensor -> eventPublisher.emit(sensor.read()));
        });
    }

    void shutdown() {
        Logger.info("Shutting down");
        killSwitch.onCompleted();
        while (true) {
            if (dead.get()) {
                break;
            }
        }

        motors.forEach(Motor::writeZero);
        lights.forEach(Light::writeZero);
        thrusters.forEach(Thruster::writeZero);
    }

    /**
     * Initialises the ROV, attaching the hardware updates to their event source. The ROV will "timeout"
     * if communication with the topside is lost or the received heartbeat value indicates a non-operational
     * status and will shutdown.
     * @param io the scheduler to use for device I/O
     * @param clock the scheduler to use for timing
     */
    void init(final Scheduler io, final Scheduler clock) {
        Logger.debug("Wiring up heartbeat, timeout, and thruster updates");
        final Observable<HeartbeatValue> timeout = Observable.just(new HeartbeatValue(false))
            .delay(MAX_HEARTBEAT_GAP, TimeUnit.SECONDS, clock)
            .doOnNext(heartbeat -> Logger.warn("Timeout while waiting for heartbeat"))
            .concatWith(Observable.never());

        final Observable<HeartbeatValue> heartbeats = eventPublisher.valuesOfType(HeartbeatValue.class);
        final CpuInformation cpuInformation = new CpuInformation(CPU_POLL_INTERVAL, TimeUnit.SECONDS);

        thrusters.forEach(Thruster::writeZero);

        cpuInformation.observe().subscribe(eventPublisher::emit, Logger::warn);
        Observable.interval(SLEEP_DURATION, TimeUnit.MILLISECONDS, clock)
            .withLatestFrom(
                heartbeats.mergeWith(timeout.takeUntil(heartbeats).repeat()), (tick, heartbeat) -> heartbeat)
            .observeOn(io)
            .takeUntil(killSwitch)
            .subscribe(this::beat, RuntimeException::new, () -> dead.set(true));
    }

    private void thrustersUpdate() {
        thrusterConfig.update();
        thrusters.forEach(Thruster::write);
    }

    private void softShutdown() {
        thrusterConfig.updateZero();
        thrusters.forEach(Thruster::writeZero);
    }

    private void beat(final HeartbeatValue heartbeat) {
        if (heartbeat.getOperational()) {
            thrustersUpdate();
            lights.forEach(Light::write);
            motors.forEach(Motor::write);
        } else {
            softShutdown();
            lights.forEach(Light::flash);
            motors.forEach(Motor::writeZero);
        }
    }

    public static void main(final String[] args) throws InterruptedException, IOException {
        final String app = "rov";
        final HelpFormatter formatter = new HelpFormatter();
        final Option broadcast = Option.builder("b")
            .longOpt("broadcast")
            .hasArg()
            .argName("ADDRESS")
            .desc("use ADDRESS to broadcast messages")
            .required()
            .build();
        final Option serialPort = Option.builder("s")
            .longOpt("serial-port")
            .hasArg()
            .argName("FILE")
            .desc("read and write to FILE as serial device")
            .required()
            .build();
        final Option baudRate = Option.builder("r")
            .type(Integer.class)
            .longOpt("baud-rate")
            .hasArg()
            .argName("BPS")
            .desc("the baud rate to use")
            .required()
            .build();

        final Options options = new Options();
        options.addOption(broadcast);
        options.addOption(serialPort);
        options.addOption(baudRate);

        try {
            final CommandLineParser parser = new DefaultParser();
            final CommandLine arguments = parser.parse(options, args);

            final InetAddress broadcastAddress = InetAddress.getByName(arguments.getOptionValue("b"));
            final int broadcastPort = BroadcastEventPublisher.DEFAULT_BROADCAST_PORT;
            final DatagramSocket socket = new DatagramSocket(broadcastPort);
            final EventPublisher eventPublisher = new BroadcastEventPublisher(new UdpBroadcast<>(
                socket, broadcastAddress, broadcastPort, new BasicOrder<>()));
            final Serial serial = SerialFactory.createInstance();
            final I2CBus bus = I2CFactory.getInstance(ALT_IMU_I2C_BUS);
            final Rov rov = new Rov(
                eventPublisher,
                new Maestro<>(serial, MAESTRO_DEVICE_NUMBER),
                new AltIMU10v3(bus, ALT_IMU_SA0_HIGH));

            Runtime.getRuntime().addShutdownHook(new Thread(rov::shutdown));

            serial.open(arguments.getOptionValue("s"), Integer.parseInt(arguments.getOptionValue("r")));
            rov.init(Schedulers.io(), Schedulers.computation());

            Logger.info("Started");
            eventPublisher.await();
        } catch (final ParseException e) {
            formatter.printHelp(app, options, true);
            System.exit(1);
        }
    }
}
