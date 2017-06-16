package com.easternedgerobotics.rov;

import com.easternedgerobotics.rov.config.CameraCalibrationConfig;
import com.easternedgerobotics.rov.config.Config;
import com.easternedgerobotics.rov.config.DistanceCalculatorConfig;
import com.easternedgerobotics.rov.config.JoystickConfig;
import com.easternedgerobotics.rov.config.LaunchConfig;
import com.easternedgerobotics.rov.config.SliderConfig;
import com.easternedgerobotics.rov.config.TopsidesConfig;
import com.easternedgerobotics.rov.config.VideoDecoderConfig;
import com.easternedgerobotics.rov.control.AutoDepth;
import com.easternedgerobotics.rov.control.ExponentialMotionScale;
import com.easternedgerobotics.rov.control.MotionReverser;
import com.easternedgerobotics.rov.control.SpeedRegulator;
import com.easternedgerobotics.rov.event.BroadcastEventPublisher;
import com.easternedgerobotics.rov.event.EventPublisher;
import com.easternedgerobotics.rov.fx.MainView;
import com.easternedgerobotics.rov.fx.ViewLoader;
import com.easternedgerobotics.rov.io.arduino.Arduino;
import com.easternedgerobotics.rov.io.arduino.ArduinoPort;
import com.easternedgerobotics.rov.io.files.TcpFileReceiver;
import com.easternedgerobotics.rov.io.files.ValueStore;
import com.easternedgerobotics.rov.io.joystick.JoystickController;
import com.easternedgerobotics.rov.io.joystick.LogitechExtremeJoystickSource;
import com.easternedgerobotics.rov.io.panel.EmergencyStopController;
import com.easternedgerobotics.rov.io.panel.MotionPowerStoreController;
import com.easternedgerobotics.rov.io.panel.SliderController;
import com.easternedgerobotics.rov.math.DistanceCalculator;
import com.easternedgerobotics.rov.measure.Depth;
import com.easternedgerobotics.rov.value.AutoDepthValue;
import com.easternedgerobotics.rov.value.CameraCalibrationValue;
import com.easternedgerobotics.rov.value.ExternalPressureValue;
import com.easternedgerobotics.rov.value.MotionPowerValue;
import com.easternedgerobotics.rov.video.CameraCalibration;
import com.easternedgerobotics.rov.video.VideoDecoder;

import javafx.application.Application;
import javafx.stage.Stage;
import org.pmw.tinylog.Logger;
import rx.broadcast.BasicOrder;
import rx.broadcast.UdpBroadcast;
import rx.schedulers.Schedulers;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public final class Topside extends Application {
    private TopsidesConfig config;

    private EventPublisher eventPublisher;

    private ViewLoader viewLoader;

    private Arduino arduino;

    private EmergencyStopController emergencyStopController;

    private SliderController sliderController;

    private MotionPowerStoreController motionPowerStoreController;

    private VideoDecoder videoDecoder;

    private CameraCalibration cameraCalibration;

    private JoystickController joystickController;

    private TcpFileReceiver fileReceiver;

    @Override
    public void init() throws SocketException, UnknownHostException {
        final Config configSource = new Config(
            getParameters().getNamed().get("default"),
            getParameters().getNamed().get("config")
        );
        final LaunchConfig launchConfig = configSource.getConfig("launch", LaunchConfig.class);
        config = configSource.getConfig("topsides", TopsidesConfig.class);

        final InetAddress broadcastAddress = InetAddress.getByName(launchConfig.broadcast());
        final int broadcastPort = launchConfig.defaultBroadcastPort();
        final DatagramSocket socket = new DatagramSocket(broadcastPort);
        eventPublisher = new BroadcastEventPublisher(new UdpBroadcast<>(
            socket, broadcastAddress, broadcastPort, new BasicOrder<>()));

        arduino = new Arduino(
            new ArduinoPort(
                config.pilotPanelName(),
                config.pilotPanelPort(),
                config.pilotPanelTimeOut(),
                config.pilotPanelBaud()),
            new byte[0],
            config.pilotPanelOutputs(),
            config.pilotPanelInputPullups());

        emergencyStopController = new EmergencyStopController(
            arduino, config.emergencyStopButtonAddress());

        sliderController = new SliderController(
            arduino,
            Schedulers.io(),
            eventPublisher,
            configSource.getConfig("slider", SliderConfig.class));

        motionPowerStoreController = new MotionPowerStoreController(
            arduino,
            config.pilotPanelInputPullups(),
            config.pilotPanelOutputs(),
            config.motionPowerSwitchDuration(),
            config.motionPowerSaveFlashCount(),
            config.motionPowerSaveFlashDuration(),
            eventPublisher,
            ValueStore.of(MotionPowerValue.class, config.preferencesHome()),
            Schedulers.io());

        videoDecoder = new VideoDecoder(
                eventPublisher,
                configSource.getConfig("videoDecoder", VideoDecoderConfig.class),
                launchConfig.broadcast());

        final ValueStore<CameraCalibrationValue> cameraCalibrationStore = ValueStore.of(
            CameraCalibrationValue.class, config.preferencesHome());

        cameraCalibration = new CameraCalibration(
            eventPublisher,
            configSource.getConfig("cameraCalibration", CameraCalibrationConfig.class),
            cameraCalibrationStore,
            Schedulers.newThread());

        fileReceiver = new TcpFileReceiver(
            launchConfig.fileReceiverPort(),
            launchConfig.fileReceiverSocketBacklog());

        final DistanceCalculator distanceCalculator = new DistanceCalculator(
            configSource.getConfig("distanceCalculator", DistanceCalculatorConfig.class),
            cameraCalibrationStore);

        viewLoader = new ViewLoader(MainView.class, "Control Software", new HashMap<Class<?>, Object>() {
            {
                put(EventPublisher.class, eventPublisher);
                put(Config.class, configSource);
                put(EmergencyStopController.class, emergencyStopController);
                put(VideoDecoder.class, videoDecoder);
                put(CameraCalibration.class, cameraCalibration);
                put(DistanceCalculator.class, distanceCalculator);
            }
        });

        final AutoDepth autoDepth = new AutoDepth(
            eventPublisher.valuesOfType(AutoDepthValue.class),
            eventPublisher.valuesOfType(ExternalPressureValue.class).map(Depth::fromPressure));

        joystickController = new JoystickController(
            eventPublisher,
            ExponentialMotionScale::apply,
            MotionReverser::apply,
            SpeedRegulator::apply,
            autoDepth::apply,
            configSource.getConfig("joystick", JoystickConfig.class));
    }

    @Override
    public void start(final Stage stage) {
        Logger.info("Starting");

        viewLoader.loadMain(stage);
        arduino.start(config.pilotPanelHeartbeatInterval(), config.pilotPanelHeartbeatTimeout(), TimeUnit.MILLISECONDS);
        joystickController.start(LogitechExtremeJoystickSource.create(
            config.joystickRecoveryInterval(), TimeUnit.MILLISECONDS, Schedulers.io()));
        fileReceiver.start();
        Logger.info("Started");
    }

    @Override
    public void stop() {
        Logger.info("Stopping");
        eventPublisher.stop();
        arduino.stop();
        sliderController.stop();
        motionPowerStoreController.stop();
        videoDecoder.stop();
        joystickController.stop();
        fileReceiver.stop();
        Logger.info("Stopped");
    }

    public static void main(final String[] args) {
        launch(args);
    }
}
