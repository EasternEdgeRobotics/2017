package com.easternedgerobotics.rov.control;

import com.easternedgerobotics.rov.value.HeartbeatValue;

import rx.Observable;

import java.awt.GridLayout;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/* *****
 * A small GUI for sending motion values (joystick) and receiving thruster values.
 * Run the main to launch the GUI.
 */
public class ThrusterTestView extends JFrame implements TestView {

    public static final long SLEEP_DURATION = 10L;
    
    private static final long MAX_HEARTBEAT_GAP = 500L;
    
    private final String broadcast = "localhost";
    
    private final int port = 8000;
    
    private static final int GRID_WIDTH = 3;
    
    private static final int GRID_HEIGHT = 4;
    
    private static final int FRAME_WIDTH = 500;
    
    private static final int FRAME_HEIGHT = 100;
    
    // Client connection state
    private boolean isOperational = false;
    
    private long lastClientUpdate = System.currentTimeMillis();
    
    private ThrusterTestModel model;
    
    public ThrusterTestView() {
        model = new ThrusterTestModel(broadcast, port);
        
        setLayout(new GridLayout(GRID_HEIGHT, GRID_WIDTH));

        final JTextField surgeInput = new JTextField(1);
        final JTextField swayInput = new JTextField(1);
        final JTextField heaveInput = new JTextField(1);
        final JTextField yawInput = new JTextField(1);
        final JTextField rollInput = new JTextField(1);
        final JButton inputSubmit = new JButton("Update inputs");
        inputSubmit.addActionListener(new MotionInputListener(surgeInput, swayInput, heaveInput,
                                                              yawInput, rollInput, model, this));
        add(new JLabel("SURGE"));
        add(new JLabel("SWAY"));
        add(new JLabel("HEAVE"));
        add(surgeInput);
        add(swayInput);
        add(heaveInput);
        add(new JLabel("YAW"));
        add(new JLabel("ROLL"));
        add(new JLabel("<for alignment>"));
        add(yawInput);
        add(rollInput);
        add(inputSubmit);
        
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        refresh();

        // Subscribe to the client heartbeats
        model.getEventPublisher().valuesOfType(HeartbeatValue.class).subscribe(heartbeatValue -> {
            isOperational = heartbeatValue.isOperational();
            lastClientUpdate = System.currentTimeMillis();
        });

        // thread to update controls and devices with
        final Observable<Boolean> operationalObserver = Observable.interval(SLEEP_DURATION, TimeUnit.MILLISECONDS)
            .map(l -> (System.currentTimeMillis() - lastClientUpdate > MAX_HEARTBEAT_GAP) && isOperational);

        // define on and off conditions for the rov
        operationalObserver.subscribe(operational -> {
            try {
                if (operational) {
                    model.update();
                    refresh();
                } else {
                    model.updateZero();
                    refresh();
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    @Override
    public void refresh() {
        
    }
    
    public static void main(final String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new ThrusterTestView();
            }
        });
    }

}
