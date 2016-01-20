package com.easternedgerobotics.rov.control;

import java.awt.GridLayout;
import java.util.concurrent.TimeUnit;

import javax.swing.*;

import com.easternedgerobotics.rov.value.HeartbeatValue;

import rx.Observable;

/* *****
 * A small GUI for sending motion values (joystick) and receiving thruster values.
 * Run the main to launch the GUI.
 */
public class ThrusterControlTestView extends JFrame {

    public static final long SLEEP_DURATION = 10L;
    private static final long MAX_HEARTBEAT_GAP = 500L;
	private final String broadcast = "localhost";
	private final int port = 8000;
    // Client connection state
    private boolean isOperational = false;
    private long lastClientUpdate = System.currentTimeMillis();
	
	private ThrusterControlTestModel model;
	private final JLabel portForeOutput = new JLabel();
	private final JLabel portAftOutput = new JLabel();
	private final JLabel portVertOutput = new JLabel();
	private final JLabel stbdForeOutput = new JLabel();
	private final JLabel stbdAftOutput = new JLabel();
	private final JLabel stbdVertOutput = new JLabel();
	private ThrusterValueListener portForeListener;
	private ThrusterValueListener portAftListener;
	private ThrusterValueListener portVertListener;
	private ThrusterValueListener stbdForeListener;
	private ThrusterValueListener stbdAftListener;
	private ThrusterValueListener stbdVertListener;
	
	public ThrusterControlTestView(){
		model = new ThrusterControlTestModel(broadcast, port);
		
		setLayout(new GridLayout(8, 3));

		portForeListener = new ThrusterValueListener(model, ThrusterControlTestModel.PORT_FORE_NAME);
		portAftListener = new ThrusterValueListener(model, ThrusterControlTestModel.PORT_AFT_NAME);
		portVertListener = new ThrusterValueListener(model, ThrusterControlTestModel.PORT_VERT_NAME);
		stbdForeListener = new ThrusterValueListener(model, ThrusterControlTestModel.STARBOARD_FORE_NAME);
		stbdAftListener = new ThrusterValueListener(model, ThrusterControlTestModel.STARBOARD_AFT_NAME);
		stbdVertListener = new ThrusterValueListener(model, ThrusterControlTestModel.STARBOARD_VERT_NAME);
		add(new JLabel("PORT FORE"));
		add(new JLabel("PORT AFT"));
		add(new JLabel("PORT VERT"));
		add(portForeOutput);
		add(portAftOutput);
		add(portVertOutput);
		add(new JLabel("STARBOARD FORE"));
		add(new JLabel("STARBOARD AFT"));
		add(new JLabel("STARBOARD VERT"));
		add(stbdForeOutput);
		add(stbdAftOutput);
		add(stbdVertOutput);

		JTextField surgeInput = new JTextField(1);
		JTextField swayInput = new JTextField(1);
		JTextField heaveInput = new JTextField(1);
		JTextField yawInput = new JTextField(1);
		JTextField rollInput = new JTextField(1);
		JButton inputSubmit = new JButton("Update inputs");
		inputSubmit.addActionListener(new MotionInputListener(surgeInput, swayInput, heaveInput, yawInput, rollInput, model, this));
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
		
		setSize(500, 200);
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
            } catch (final Exception e) { //IOException e) {
                e.printStackTrace();
            }
        });
	}
	
	void refresh(){
		portForeOutput.setText(String.valueOf(portForeListener.getOutput()));
		portAftOutput.setText(String.valueOf(portAftListener.getOutput()));
		portVertOutput.setText(String.valueOf(portVertListener.getOutput()));
		stbdForeOutput.setText(String.valueOf(stbdForeListener.getOutput()));
		stbdAftOutput.setText(String.valueOf(stbdAftListener.getOutput()));
		stbdVertOutput.setText(String.valueOf(stbdVertListener.getOutput()));
	}
	
	public static void main(String args[]){
		SwingUtilities.invokeLater(new Runnable(){
			public void run(){
				new ThrusterControlTestView();
			}
		});
	}


}
