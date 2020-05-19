package com.validyne.blutransducer;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

/**
 * This is the calibration activity that allows a user to send bytes to the rfduino chip to control the
 * calibration of the transducer
 */
public class Calibration extends AppCompatActivity implements ServiceConnection {
    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTED = 4;

    private RFduinoService rFduinoService;
    private ProgressDialog progressDialog;
    UnitConversionHelper unitConversionHelper;
    private int state;
    private String units;
    TextView pressure;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calibration);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            state = extras.getInt("state");
            units = extras.getString("units");
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage(getString(R.string.please_wait));
        progressDialog.setCancelable(false);

        pressure = (TextView) findViewById(R.id.pressure);

        Button plus = (Button) findViewById(R.id.btn_plus);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rFduinoService.send(new byte[]{3});
            }
        });

        Button minus = (Button) findViewById(R.id.btn_minus);
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rFduinoService.send(new byte[]{4});
            }
        });


        Button span = (Button) findViewById(R.id.btn_span);
        span.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rFduinoService.send(new byte[]{5});
                progressDialog.setTitle("Setting Span");
                progressDialog.show();
            }
        });

        Button zero = (Button) findViewById(R.id.btn_zero);
        zero.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rFduinoService.send(new byte[]{6});
                progressDialog.setTitle("Setting Zero");
                progressDialog.show();
            }
        });

        Button restore = (Button) findViewById(R.id.btn_restore);
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rFduinoService.send(new  byte[]{7});
                progressDialog.setTitle("Restoring Post Calibration");
                progressDialog.show();
            }
        });
    }

    /**
     * This method adds data to the pressure textbox that is sent from the rfduino / transducer
     * @param data
     */
    private void addData(byte[] data) {
        String ascii = HexAsciiHelper.bytesToAsciiMaybe(data);

        Log.i("BTLE", "Receiving: " + ascii);
        if (ascii != null) {
            String[] dataString = ascii.split("\\*+");
            Log.i("I/BTLE","Data: " + " = " + Arrays.toString(dataString));
            if (dataString[0].equals("P")) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                String unitMeasurement = sharedPreferences.getString(getString(R.string.pressureType), units);
                pressure.setText(unitConversionHelper.convertPressure(units, unitMeasurement, Double.parseDouble(dataString[1])) + " " + unitMeasurement);
            } else if (dataString[0].equals("+")) {
                if (ascii.contains("?")) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(Calibration.this);
                    builder.setMessage(R.string.plus_error).setTitle(R.string.calibration_dialog_title);
                    builder.setIconAttribute(android.R.attr.alertDialogIcon);
                    builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                } else {
                    Toast.makeText(getApplicationContext(), "Plus was successful", Toast.LENGTH_SHORT).show();
                }
            } else if (dataString[0].equals("-")) {
                if (ascii.contains("?")) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(Calibration.this);
                    builder.setMessage(R.string.minus_error).setTitle(R.string.calibration_dialog_title);
                    builder.setIconAttribute(android.R.attr.alertDialogIcon);
                    builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                } else {
                    Toast.makeText(getApplicationContext(), "Minus was successful", Toast.LENGTH_SHORT).show();
                }
            } else if (dataString[0].equals("S")) {
                if (ascii.contains("?")) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(Calibration.this);
                    builder.setMessage(R.string.span_error).setTitle(R.string.calibration_dialog_title);
                    builder.setIconAttribute(android.R.attr.alertDialogIcon);
                    builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                } else {
                    Toast.makeText(getApplicationContext(), "Set span was successful", Toast.LENGTH_SHORT).show();
                }
                progressDialog.dismiss();
            } else if (dataString[0].equals("Z")) {
                if (ascii.contains("?")) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(Calibration.this);
                    builder.setMessage(R.string.zero_error).setTitle(R.string.calibration_dialog_title);
                    builder.setIconAttribute(android.R.attr.alertDialogIcon);
                    builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                } else {
                    Toast.makeText(getApplicationContext(), "Set zero was successful", Toast.LENGTH_SHORT).show();
                }
                progressDialog.dismiss();
            } else if (dataString[0].equals("R")) {
                if (ascii.length() == 12) {
                    progressDialog.dismiss();
                    Toast.makeText(getApplicationContext(), "Loading of post calibration was successful", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * BroadcastReceiver to receive information from the rfduino chip
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
     * This method binds the RFduinoService to this activity
     * @param name ComponentName
     * @param binder Ibinder from the service
     */
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        RFduinoService.LocalBinder b = (RFduinoService.LocalBinder) binder;
        rFduinoService = b.getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        rFduinoService = null;
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Registering receivers for BLE communication
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Binding this activity to the custom rfduino service
        Intent rfduinoIntent = new Intent(Calibration.this, RFduinoService.class);
        bindService(rfduinoIntent, this, BIND_AUTO_CREATE);

    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
        unbindService(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        rFduinoService = null;
        downgradeState(STATE_DISCONNECTED);
    }
}