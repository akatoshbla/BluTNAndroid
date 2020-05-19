package com.validyne.blutransducer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import com.aigestudio.wheelpicker.WheelPicker;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * This activity is the main activity that allows you to pick a rfduino chip to connect too.
 */
public class MainActivity extends AppCompatActivity implements WheelPicker.OnItemSelectedListener {

    final static private int REQUEST_ENABLE_BT = 1;

    List<String> serialNums;
    String pickerData;
    BluetoothAdapter bluetoothAdapter;
    WheelPicker wheelPicker;
    boolean isConnected;
    boolean scanning;
    boolean scanStarted;
    BluetoothLeScanner bluetoothLeScanner;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    List<BluetoothDevice> bluetoothDeviceList;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        bluetoothDeviceList = new ArrayList<>();
        serialNums = new ArrayList<>();
        isConnected = false;

        // Initializes Bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Setup wheel
        wheelPicker = (WheelPicker) findViewById(R.id.wheel);
        wheelPicker.setOnItemSelectedListener(this);
        wheelPicker.setSelectedItemTextColor(Color.BLACK);
        wheelPicker.setItemAlign(3);
        wheelPicker.setAtmospheric(true);
        wheelPicker.setCurved(true);
        wheelPicker.isCyclic();

        // Populate wheel
        wheelPicker.setData(serialNums);

        // Onclick listener for connect button
        Button connect = (Button) findViewById(R.id.button);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothDeviceList.isEmpty()) {
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                    alertDialog.setTitle("No Transducer Selected");
                    alertDialog.setMessage("Please select a transducer from the list above. If no transducer is listed above, then please try to get closer to the device(s).");
                    alertDialog.setIconAttribute(android.R.attr.alertDialogIcon);
                    alertDialog.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    alertDialog.show();
                } else {
                    Intent intent = new Intent(getBaseContext(), Pressure.class);
                    for (BluetoothDevice device : bluetoothDeviceList) {
                        if (device.getName().equals(pickerData)) {
                            intent.putExtra("address", device.getAddress());
                        }
                    }
                    startActivity(intent);
                }
            }
        });
    }

    /**
     * This method asks for permissions to use the bluetooth adapter to locate other BLE devices.
     * @param requestCode Request Code for asking permission to use BLE
     * @param permissions Array of permissions
     * @param grandResults Permission change you wish to make
     */
    @Override
    @SuppressWarnings("all")
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grandResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grandResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Info:", "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }

    /**
     * ScanCallBack that finds bluetooth devices and adds them to wheel picker
     */
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice btDevice = result.getDevice();
            if (!serialNums.contains(btDevice.getName())) {
                serialNums.add(btDevice.getName());
                Log.i("BT", BluetoothHelper.getDeviceInfoText(btDevice, result.getRssi(), result.getScanRecord().getBytes()));
                wheelPicker.setData(serialNums);
                bluetoothDeviceList.add(btDevice);
            }
        }
    };

    /**
     * Get status of scan and start scan
     */
    @SuppressWarnings("unused")
    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
            scanStarted &= scanning;
            // update refresh icon
        }
    };

    /**
     * Adds text value of the currently selected item on wheel picker
     * @param picker WheelPicker object
     * @param data The data of the currently selected item on picker
     * @param position The current position of the picker
     */
    @Override
    public void onItemSelected(WheelPicker picker, Object data, int position) {
        pickerData = String.valueOf(data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        MenuItem settings = menu.findItem(R.id.settings);
        MenuItem refresh = menu.findItem(R.id.action_refresh);
        settings.setVisible(false);
        startRefresh(refresh);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Animates the refresh icon in the menu bar to rotate 360 degrees
     * @param item MenusItem object
     */
    @SuppressWarnings("all")
    public void startRefresh(MenuItem item) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageView imageView = (ImageView) inflater.inflate(R.layout.refresh, null);
        Animation rotation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh);
        rotation.setRepeatCount(Animation.INFINITE);
        imageView.startAnimation(rotation);
        item.setActionView(imageView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        bluetoothLeScanner.stopScan(mScanCallback);
        bluetoothDeviceList.clear();
        serialNums.clear();
        wheelPicker.setData(serialNums);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check to ensure bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        } else {
            Log.i("BTLE", ": Bluetooth enabled");
            getPermission();
            ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(RFduinoService.PUUID_SERVICE).build();
            List<ScanFilter> scanFilters = new ArrayList<>();
            scanFilters.add(scanFilter);
            ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            if (bluetoothLeScanner != null) {
                Log.i("BTLE", ": BluetoothLeScanner has started from a resume");
                bluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback);
            } else {
                Log.i("BTLE", ": BluetoothLeScanner has started from initial startup");
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                bluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback);
            }
        }
    }

    /**
     * Asks for permission to access coarse location to get BLE to work
     */
    private void getPermission() {
        // Ask user for location permissions (API 21+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.i("BTLE", "Dialog for access was sent");
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @SuppressLint("NewApi")
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            } else {
                Log.i("BTLE", "Dialog for access was not needed");
            }
        } else {
            Log.i("BTLE", "SDK is lower than 21, no need for permission dialog");
        }
    }
}