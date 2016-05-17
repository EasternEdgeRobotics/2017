package com.easternedgerobotics.rov;

import com.easternedgerobotics.rov.control.SixThrusterConfig;
import com.easternedgerobotics.rov.event.BroadcastEventPublisher;
import com.easternedgerobotics.rov.event.EventPublisher;
import com.easternedgerobotics.rov.io.CpuInformation;
import com.easternedgerobotics.rov.io.LM35;
import com.easternedgerobotics.rov.io.MPX4250AP;
import com.easternedgerobotics.rov.io.Motor;
import com.easternedgerobotics.rov.io.Thruster;
import com.easternedgerobotics.rov.io.pololu.PololuMaestro;
import com.easternedgerobotics.rov.io.pololu.PololuMaestroInputChannel;
import com.easternedgerobotics.rov.io.pololu.PololuMaestroOutputChannel;
import com.easternedgerobotics.rov.value.AftCameraSpeedValue;
import com.easternedgerobotics.rov.value.ExternalTemperatureValue;
import com.easternedgerobotics.rov.value.HeartbeatValue;
import com.easternedgerobotics.rov.value.InternalPressureValue;
import com.easternedgerobotics.rov.value.InternalTemperatureValue;
import com.easternedgerobotics.rov.value.PortAftSpeedValue;
import com.easternedgerobotics.rov.value.PortForeSpeedValue;
import com.easternedgerobotics.rov.value.PortVertSpeedValue;
import com.easternedgerobotics.rov.value.SpeedValue;
import com.easternedgerobotics.rov.value.StarboardAftSpeedValue;
import com.easternedgerobotics.rov.value.StarboardForeSpeedValue;
import com.easternedgerobotics.rov.value.StarboardVertSpeedValue;

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
import rx.broadcast.SingleSourceFifoOrder;
import rx.broadcast.UdpBroadcast;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class Rov {
    private static final long MAX_HEARTBEAT_GAP = 5;

    private static final long CPU_POLL_INTERVAL = 1;

    private static final long SLEEP_DURATION = 100;

    private static final byte MAESTRO_DEVICE_NUMBER = 0x01;

    private static final byte PORT_AFT_CHANNEL = 17;

    private static final byte STARBOARD_AFT_CHANNEL = 14;

    private static final byte PORT_FORE_CHANNEL = 15;

    private static final byte STARBOARD_FORE_CHANNEL = 12;

    private static final byte PORT_VERT_CHANNEL = 16;

    private static final byte STARBOARD_VERT_CHANNEL = 13;

    private static final byte AFT_CAMERA_MOTOR_CHANNEL = 18;

    private static final byte INTERNAL_TEMPERATURE_SENSOR_CHANNEL = 1;

    private static final byte EXTERNAL_TEMPERATURE_SENSOR_CHANNEL = 3;

    private static final byte INTERNAL_PRESSURE_SENSOR_CHANNEL = 2;

    private final LM35 internalTemperatureSensor;

    private final LM35 externalTemperatureSensor;

    private final MPX4250AP internalPressureSensor;

    private final SixThrusterConfig thrusterConfig;

    private final List<Thruster> thrusters;

    private final List<Motor> motors;

    private final EventPublisher eventPublisher;

    private Rov(final EventPublisher eventPublisher, final Serial serial) throws IOException {
        this.eventPublisher = eventPublisher;

        final PololuMaestro maestro = new PololuMaestro(serial, MAESTRO_DEVICE_NUMBER);

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
                    .valuesOfType(AftCameraSpeedValue.class)
                    .startWith(new AftCameraSpeedValue())
                    .cast(SpeedValue.class),
                new PololuMaestroOutputChannel(maestro, AFT_CAMERA_MOTOR_CHANNEL, Motor.MAX_FWD, Motor.MAX_REV))
        ));

        this.thrusters = Collections.unmodifiableList(Arrays.asList(
            new Thruster(
                eventPublisher
                    .valuesOfType(PortAftSpeedValue.class)
                    .startWith(portAft)
                    .cast(SpeedValue.class),
                new PololuMaestroOutputChannel(maestro, PORT_AFT_CHANNEL, Thruster.MAX_FWD, Thruster.MAX_REV)),
            new Thruster(
                eventPublisher
                    .valuesOfType(StarboardAftSpeedValue.class)
                    .startWith(starboardAft)
                    .cast(SpeedValue.class),
                new PololuMaestroOutputChannel(maestro, STARBOARD_AFT_CHANNEL, Thruster.MAX_FWD, Thruster.MAX_REV)),
            new Thruster(
                eventPublisher
                    .valuesOfType(PortForeSpeedValue.class)
                    .startWith(portFore)
                    .cast(SpeedValue.class),
                new PololuMaestroOutputChannel(maestro, PORT_FORE_CHANNEL, Thruster.MAX_FWD, Thruster.MAX_REV)),
            new Thruster(
                eventPublisher
                    .valuesOfType(StarboardForeSpeedValue.class)
                    .startWith(starboardFore)
                    .cast(SpeedValue.class),
                new PololuMaestroOutputChannel(maestro, STARBOARD_FORE_CHANNEL, Thruster.MAX_FWD, Thruster.MAX_REV)),
            new Thruster(
                eventPublisher
                    .valuesOfType(PortVertSpeedValue.class)
                    .startWith(portVert)
                    .cast(SpeedValue.class),
                new PololuMaestroOutputChannel(maestro, PORT_VERT_CHANNEL, Thruster.MAX_FWD, Thruster.MAX_REV)),
            new Thruster(
                eventPublisher
                    .valuesOfType(StarboardVertSpeedValue.class)
                    .startWith(starboardVert)
                    .cast(SpeedValue.class),
                new PololuMaestroOutputChannel(maestro, STARBOARD_VERT_CHANNEL, Thruster.MAX_FWD, Thruster.MAX_REV))
        ));

        this.internalTemperatureSensor = new LM35(
            new PololuMaestroInputChannel(maestro, INTERNAL_TEMPERATURE_SENSOR_CHANNEL));
        this.externalTemperatureSensor = new LM35(
            new PololuMaestroInputChannel(maestro, EXTERNAL_TEMPERATURE_SENSOR_CHANNEL));
        this.internalPressureSensor = new MPX4250AP(
            new PololuMaestroInputChannel(maestro, INTERNAL_PRESSURE_SENSOR_CHANNEL));
    }

    /**
     * Initialises the ROV, attaching the hardware updates to their event source. The ROV will "timeout"
     * if communication with the topside is lost or the received heartbeat value indicates a non-operational
     * status and will shutdown.
     */
    private void init() {
        Logger.debug("Wiring up heartbeat, timeout, and thruster updates");
        final Observable<HeartbeatValue> timeout = Observable.just(new HeartbeatValue(false))
            .delay(MAX_HEARTBEAT_GAP, TimeUnit.SECONDS)
            .doOnNext(heartbeat -> Logger.warn("Timeout while waiting for heartbeat"))
            .concatWith(Observable.never());

        final Observable<HeartbeatValue> heartbeats = eventPublisher.valuesOfType(HeartbeatValue.class);
        final CpuInformation cpuInformation = new CpuInformation(CPU_POLL_INTERVAL, TimeUnit.SECONDS);

        thrusters.forEach(Thruster::writeZero);

        cpuInformation.observe().subscribe(eventPublisher::emit);
        Observable.interval(SLEEP_DURATION, TimeUnit.MILLISECONDS)
            .withLatestFrom(
                heartbeats.mergeWith(timeout.takeUntil(heartbeats).repeat()), (tick, heartbeat) -> heartbeat)
            .observeOn(Schedulers.io())
            .subscribe(this::beat, RuntimeException::new);
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
            motors.forEach(Motor::write);
        } else {
            softShutdown();
            motors.forEach(Motor::writeZero);
        }

        eventPublisher.emit(new InternalTemperatureValue(internalTemperatureSensor.read()));
        eventPublisher.emit(new ExternalTemperatureValue(externalTemperatureSensor.read()));
        eventPublisher.emit(new InternalPressureValue(internalPressureSensor.read()));
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
                socket, broadcastAddress, broadcastPort, new SingleSourceFifoOrder<>(SingleSourceFifoOrder.DROP_LATE)));
            final Serial serial = SerialFactory.createInstance();
            final Rov rov = new Rov(eventPublisher, serial);

            serial.open(arguments.getOptionValue("s"), Integer.parseInt(arguments.getOptionValue("r")));
            rov.init();

            Logger.info("Started");
            eventPublisher.await();
        } catch (final ParseException e) {
            formatter.printHelp(app, options, true);
            System.exit(1);
        }
    }
}
