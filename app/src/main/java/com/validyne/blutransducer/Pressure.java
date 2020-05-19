package com.validyne.blutransducer;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Arrays;

/**
 * This is the Pressure activity in which we connect to the rfduino chip and read information from the
 * Transducer.
 */
public class Pressure extends AppCompatActivity {

    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private volatile boolean threadSuspended;
    private String deviceAddress;
    private String unitMeasurement;
    private String factoryUnitPressure;
    private char unitTemperature;
    private RFduinoService rfduinoService;
    private int state;
    private double realTemperature;
    private double realPressure;
    private double fsPressure;
    private ProgressBar bar;
    private boolean finishedLoading;
    Button calibrate;
    TextView textView;
    TextView textView2;
    TextView textView3;
    TextView textView4;
    TextView textView5;
    TextView model;
    TextView serial;
    TextView temperature;
    TextView gaugeText;
    TextView pressure;
    TextView calibrated;
    TextView loadingText;
    TextView zero;
    TextView minus;
    TextView plus;
    pl.pawelkleczkowski.customgauge.CustomGauge gauge;
    UnitConversionHelper unitConversionHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pressure);

        // Show Overlay
        loadingText = (TextView) findViewById(R.id.loading);
        bar = (ProgressBar) this.findViewById(R.id.progressBar);
        finishedLoading = false;
        new ProgressTask().execute();

        // Get address of the rfduino the custom wishes to connect too
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            deviceAddress = extras.getString("address");
        }

        factoryUnitPressure = "";
        realTemperature = 0.0;
        realPressure = 0.0;
        fsPressure = 0.0;
        textView = (TextView) findViewById(R.id.textView);
        textView2 = (TextView) findViewById(R.id.textView2);
        textView3 = (TextView) findViewById(R.id.textView3);
        textView4 = (TextView) findViewById(R.id.textView4);
        textView5 = (TextView) findViewById(R.id.textView5);
        model = (TextView) findViewById(R.id.model_number);
        serial = (TextView) findViewById(R.id.serial_number);
        pressure = (TextView) findViewById(R.id.max_pressure);
        calibrated = (TextView) findViewById(R.id.last_calibrated);
        temperature = (TextView) findViewById(R.id.temperature);
        gaugeText = (TextView) findViewById(R.id.gaugeText);
        zero = (TextView) findViewById(R.id.zero);
        minus = (TextView) findViewById(R.id.minus);
        plus = (TextView) findViewById(R.id.plus);
        gauge = (pl.pawelkleczkowski.customgauge.CustomGauge) findViewById(R.id.gauge);
        calibrate = (Button) findViewById(R.id.button);
        calibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), Calibration.class);
                intent.putExtra("state", state);
                intent.putExtra("units", factoryUnitPressure);
                startActivity(intent);
            }
        });

        // Thread to check Temperature
        final Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    while(!isInterrupted()) {
                        Thread.sleep(5000);

                        if (!threadSuspended) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    rfduinoService.send(new byte[]{2});
                                }
                            });
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e("Thread", "Error in local thread - ", e);
                }
            }
        };
        thread.start();
    }

    /**
     * This method gets the saved user settings. Includes units of measurements for pressure and temperature
     */
    @SuppressWarnings("all")
    private void getUserSettings() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        unitMeasurement = sharedPreferences.getString(getString(R.string.pressureType), factoryUnitPressure);
        gaugeText.setText(unitConversionHelper.convertPressure(factoryUnitPressure, unitMeasurement, realPressure) + " " + unitMeasurement);
        if (sharedPreferences.getBoolean(getString(R.string.temperatureType), false)) {
            unitTemperature = 'C';
            temperature.setText(unitConversionHelper.fahrenheitToCelsius(unitTemperature, realTemperature) + "\u00B0" + unitTemperature);
        } else {
            unitTemperature = 'F';
            temperature.setText(realTemperature + "\u00B0" + unitTemperature);
        }
    }

    /**
     * BroadcastReceiver async call that gets the data from the rfduino when it updates
     */
    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                Log.i("BTLE ", "Bluetooth is connected to Service");
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                Log.i("BTLE ", "Bluetooth is disconnected to Service");
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    /**
     * BroadcastReceiver that gest the current state of connect between phone BLE and rfduino
     */
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        MenuItem refresh = menu.findItem(R.id.action_refresh);
        refresh.setVisible(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, Settings.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This method adds the recieved data to the pressure UI elements.
     * @param data bytes recieved from the rfduino
     */
    @SuppressWarnings("all")
    private void addData(byte[] data) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String ascii = HexAsciiHelper.bytesToAsciiMaybe(data);

        Log.i("BTLE", "Receiving: " + ascii);
        if (ascii != null) {
            String[] dataString = ascii.split("\\*+");
                Log.i("I/BTLE","Data: " + " = " + Arrays.toString(dataString));
                if (dataString[0].equals("M")) {
                    model.setText(dataString[1]);
                } else if (dataString[0].equals("N")) {
                    serial.setText(dataString[1]);
                    if (!sharedPreferences.contains(getString(R.string.firstStart) + dataString[1])) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(getString(R.string.firstStart) + dataString[1], true);
                        editor.commit();
                    }
                } else if (dataString[0].equals("D")) {
                    calibrated.setText(dataString[1]);
                } else if (dataString[0].equals("F")) {
                    //pressure.setText(dataString[1]);
                    fsPressure = Double.parseDouble(dataString[1]);
                    realPressure = Double.parseDouble(dataString[1]);
                    if (factoryUnitPressure.equals("")) {
                        boolean firstStart = sharedPreferences.getBoolean(getString(R.string.firstStart) + model.getText(), true);
                        factoryUnitPressure = dataString[2].toLowerCase();
                        if (firstStart) {
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(getString(R.string.pressureType) + model.getText(), dataString[2].toLowerCase());
                            editor.putBoolean(getString(R.string.firstStart) + model.getText(), false);
                            editor.commit();
                            unitMeasurement = factoryUnitPressure;
                        }
                        else {
                            getUserSettings();
                        }
                    }
                    Log.i("unitMeasure", "value:" + unitMeasurement);
                    pressure.setText(unitConversionHelper.convertPressure(factoryUnitPressure, unitMeasurement, realPressure));
                    textView3.setText(textView3.getText() + " " + unitMeasurement);
                } else if (dataString[0].equals("T")) {
                    realTemperature = Double.parseDouble(dataString[1]);
                    temperature.setText(unitConversionHelper.fahrenheitToCelsius(unitTemperature, realTemperature) + "\u00B0" + unitTemperature);
                    finishedLoading = true;
                } else if (dataString[0].equals("P")) {
                    realPressure = Double.parseDouble(dataString[1]);
                    gaugeText.setText(unitConversionHelper.convertPressure(factoryUnitPressure, unitMeasurement, realPressure) + " " + unitMeasurement);
                    double gaugeValue = ((realPressure / fsPressure) *50) + 50;
                    Log.i("I/BTLE","Gauge Value: " + realPressure);
                    if (gaugeValue <= 0) {
                        gauge.setValue(0);
                    } else if (gaugeValue >= 100) {
                        gauge.setValue(100);
                    } else {
                        gauge.setValue((int) gaugeValue);
                    }
                }
        }
        update(realPressure);
        updateFullScale(realPressure);
    }

    /**
     * This method updates the Full Scale pressure textbox and value
     * @param press current pressure of transducer from RFDUINO
     */
    private void updateFullScale(double press) {

        textView3.setText("Full Scale " + unitMeasurement);
        pressure.setText(unitConversionHelper.convertPressure(factoryUnitPressure, unitMeasurement, fsPressure));
    }

    /**
     * This method only updates the colors of the temp text and gauge components
     */
    private void update(double press) {
        double percent = Math.abs(press / fsPressure * 100);
//        Log.i("Gauge Percent", " " + percent);
//        double temp = 0.0;
//        try {
//            temp = NumberFormat.getInstance().parse(temperature.getText().toString()).doubleValue();
//        } catch (ParseException e) { Log.i("Temp_Parse_Error", " Error parsing temperature - " + e); }
//        if (unitTemperature == 'F') {
//            if (temp < 78) { // neet adjustments
//                temperature.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.startpoint));
//            } else if (temp >= 78 && temp < 95) {
//                temperature.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.gettingHot));
//            } else {
//                temperature.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.hot));
//            }
//        } else { // need adjustments
//            if (temp < 26) {
//                temperature.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.startpoint));
//            } else if (temp >= 26 && temp < 35) {
//                temperature.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.gettingHot));
//            } else {
//                temperature.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.hot));
//            }
//        }

        if (percent < 40.00) {
            gaugeText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.startpoint));
            gauge.setPointStartColor(ContextCompat.getColor(getApplicationContext(), R.color.startpoint));
            gauge.setPointEndColor(ContextCompat.getColor(getApplicationContext(), R.color.startpoint));
        } else if (percent >= 40.00 && percent < 75.00) {
            gaugeText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.gettingHot));
            gauge.setPointStartColor(ContextCompat.getColor(getApplicationContext(), R.color.gettingHot));
            gauge.setPointEndColor(ContextCompat.getColor(getApplicationContext(), R.color.gettingHot));
        } else {
            gaugeText.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.hot));
            gauge.setPointStartColor(ContextCompat.getColor(getApplicationContext(), R.color.hot));
            gauge.setPointEndColor(ContextCompat.getColor(getApplicationContext(), R.color.hot));
        }
    }

    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {

        state = newState;
    }

    /**
     * This method is an Async call that binds the connection to the rfduino to a custom service that runs
     * in the back ground.
     */
    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rfduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rfduinoService.initialize()) {
                if (rfduinoService.connect(deviceAddress)) {
                    upgradeState(STATE_CONNECTING);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            rfduinoService = null;
            downgradeState(STATE_DISCONNECTED);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        // Registering receivers
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());
    }

    @Override
    protected void onResume() {
        super.onResume();

        getUserSettings();
        Intent rfduinoIntent = new Intent(Pressure.this, RFduinoService.class);
        bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
        threadSuspended = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
        threadSuspended = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(rfduinoServiceConnection);
    }

    /**
     * This class is to unhide all GUI elements and show a quick loading screen to get the first burst
     * of information from the rfduino / transducer. After the burst of data is recieved all GUI elements
     * are shown with the current values.
     */
    @SuppressWarnings("all")
    private class ProgressTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute(){
            super.onPreExecute();
            loadingText.setVisibility(View.VISIBLE);
            bar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... params) {
            while(!finishedLoading) { }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            loadingText.setVisibility(View.GONE);
            bar.setVisibility(View.GONE);
            textView.setVisibility(View.VISIBLE);
            textView2.setVisibility(View.VISIBLE);
            textView3.setVisibility(View.VISIBLE);
            textView4.setVisibility(View.VISIBLE);
            textView5.setVisibility(View.VISIBLE);
            model.setVisibility(View.VISIBLE);
            serial.setVisibility(View.VISIBLE);
            temperature.setVisibility(View.VISIBLE);
            pressure.setVisibility(View.VISIBLE);
            calibrated.setVisibility(View.VISIBLE);
            gauge.setVisibility(View.VISIBLE);
            gaugeText.setVisibility(View.VISIBLE);
            calibrate.setVisibility(View.VISIBLE);
            zero.setVisibility(View.VISIBLE);
            minus.setVisibility(View.VISIBLE);
            plus.setVisibility(View.VISIBLE);
        }
    }
}