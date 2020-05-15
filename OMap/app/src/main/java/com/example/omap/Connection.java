package com.example.omap;


import android.app.Activity;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

public class Connection {
    private int joystickAngle, joystickDistance, buttonState, rotationValue;
    private int x, y;

    private BufferedReader in = null;
    private String serverData = null;
    private Timer myTimer;
    private final Activity parent;

    private boolean connected;

    private static final String TAG;
    private static int timeout;
    private static String HOST;
    private static int PORT;

    static {
        TAG = "Connection";
        timeout = 3000; // milliseconds
        HOST = "192.168.0.101";
        PORT = 8001;
    }

    public Connection(Activity parent)
    {
        this.parent = parent;
    }

    public void connect()
    {
        myTimer = new Timer();
        try
        {
            myTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    TimerMethod();
                }

            }, 0, 1000);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        new Thread(new Runnable() {

            public void run() {
                while(true) {
                    try {
                        Thread.sleep(timeout);
                        connected = false;
                        Socket s = new Socket(HOST, PORT);
                        s.setSoTimeout(timeout);
                        connected = true;
                        Log.d("Socket", "Connection: " + s.isConnected());

                        while (true) {
                            in = null;
                            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                            serverData = in.readLine();
                            //                        Log.d(TAG, "run: serverdata:" + serverData);
                            //String regex = "[0-9:]+";
                            //if (serverData.matches(regex) == true)

                            String delims = ":";
                            String[] sensor = serverData.split(delims);
//                            System.out.println("sensor:" + Arrays.toString(sensor));

                            if (sensor.length >= 4) {
//                                joystickAngle = Integer.parseInt(sensor[0]);
//                                joystickDistance = Integer.parseInt(sensor[1]);
                                x = Integer.parseInt(sensor[0]);
                                y = Integer.parseInt(sensor[1]);
                                rotationValue = Integer.parseInt(sensor[2]);
                                buttonState = Integer.parseInt(sensor[3]);
                                //                                Log.d(TAG, "run: joystickAngle:" + joystickAngle + " joystickDistance:" + joystickDistance);
                            }

                        }

                    } catch (SocketTimeoutException e) {
                        Log.d(TAG, "run: disconnected from " + HOST + ":" + PORT);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public boolean isConnected() {
        return this.connected;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }


    public int getAngle()
    {
        return this.joystickAngle;
    }

    public int getDistance()
    {
        return this.joystickDistance;
    }

    public int getRotationValue(){return rotationValue;}

    public boolean getButtonState(){
        boolean buttonPressed = false;

        if (buttonState == 0)
            buttonPressed = false;
        else
            buttonPressed = true;

        return buttonPressed;
    }


    private void TimerMethod()
    {

        parent.runOnUiThread(Timer_Tick);

    }

    private Runnable Timer_Tick = new Runnable() {
        public void run() {

            //this.invalidate();

        }
    };


}
