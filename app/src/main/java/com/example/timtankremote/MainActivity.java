package com.example.timtankremote;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

   public static final int REQUEST_LOCATION_ENABLE_CODE = 101;
    public static final int SEARCH_REQUEST_CODE = 191;
    private static final String TAG = "MainActivity";

    private static final String fileNameString = "my_preferences";

    private static SharedPreferences sharedPreferences;
    private String mobAddress;
    private String mobName;

    private BluetoothDevice bluetoothDevice;
    private boolean mScanning;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothAdapter mBluetoothAdapter;

    //private BluetoothGattCharacteristic mNotifyCharacteristic;
    private boolean mConnected = false;

    private BluetoothLEService mBluetoothLEService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Your devices that don't support BLE",
                Toast.LENGTH_LONG).show();
            finish();
        }

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_ENABLE_CODE);
        }


        // Load SearchActivity for search BLE device
        final Button but_find = (Button) findViewById(R.id.butFind);
        but_find.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                startActivityForResult(intent, SEARCH_REQUEST_CODE);
            }
        });


        final Button but_connect = (Button) findViewById(R.id.butConnect);
        but_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gattServiceIntent = new Intent(MainActivity.this,
                        BluetoothLEService.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                final boolean result =
                        mBluetoothLEService.connect(mobAddress);
                Log.d(TAG, "Connect request result=" + result);

                /*
                if (bluetoothDevice != null) {

                    Intent gattServiceIntent = new Intent(MainActivity.this,
                            BluetoothLEService.class);
                    bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

                    // register mGattUpdateReceiver
                    registerReceiver(mGattUpdateReceiver, GattUpdateIntentFilter());
                    if (mBluetoothLEService != null) {
                        final boolean result =
                                mBluetoothLEService.connect(mobAddress);
                        Log.d(TAG, "Connect request result=" + result);
                    }
                }
                */

            }
        });
        but_connect.setClickable(false);

        Context context = this;
        BluetoothManager bluetoothManager = (BluetoothManager)
                context.getSystemService(context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (!mBluetoothAdapter.enable()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, Constants.REQUEST_BLUETOOTH_ENABLE_CODE);
        }

        sharedPreferences = getApplicationContext().getSharedPreferences(fileNameString, MODE_PRIVATE);
        mobAddress = sharedPreferences.getString("MOB_ADDRESS", null);
        mobName = sharedPreferences.getString("MOB_NAME", null);
        if (mobAddress != null) {
            bluetoothDevice = mBluetoothAdapter.getRemoteDevice(mobAddress);
            if (bluetoothDevice != null) {
                final TextView tvn = findViewById(R.id.textLeName);
                tvn.setText(mobName);
                but_connect.setClickable(true);
            }
        }
        /*
        Intent gattServiceIntent = new Intent(MainActivity.this,
                BluetoothLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        */
    }

    // Result of SearchActivity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult");
        if ((requestCode == SEARCH_REQUEST_CODE) && (resultCode == RESULT_OK)) {
            //Retrieve data in the intent
            String addressValue = data.getStringExtra("ADDRESS");
            String nameValue = data.getStringExtra("NAME");
            final TextView tvn = findViewById(R.id.textLeName);
            tvn.setText(nameValue);
            final TextView tva = findViewById(R.id.textInfo);
            tva.setText(addressValue);
            Log.d(TAG, "onActivityResult: " + nameValue + ", " + addressValue);
            if (addressValue != null) {
                bluetoothDevice = mBluetoothAdapter.getRemoteDevice(addressValue);
                if (bluetoothDevice != null) {
                    final TextView tvl = findViewById(R.id.textLeName);
                    tvl.setText(nameValue);
                    // save device to the shared preferences
                    SharedPreferences.Editor editor =sharedPreferences.edit();
                    editor.putString("MOB_ADDRESS", addressValue);
                    editor.putString("MOB_NAME", nameValue);
                    editor.apply();
                    mobAddress = addressValue;
                    mobName = nameValue;
                    findViewById(R.id.butConnect).setClickable(true);
                }
            }

        }
    }
/*
    protected  void connectService() {
        if (mNotifyCharacteristic != null) {
            final int charaProp = mNotifyCharacteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                mBluetoothLEService.readCharacteristic(mNotifyCharacteristic);
            }
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                mBluetoothLEService.setCharacteristicNotification(mNotifyCharacteristic,
                        true);
            }
        }
    }
*/
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLEService = ((BluetoothLEService.LocalBinder) service).getService();
            if (!mBluetoothLEService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            //mBluetoothLEService.connect(bluetoothDevice.getAddress());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLEService = null;
        }
    };


    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (BluetoothLEService.ACTION_GATT_CONNECTED.equals(action)) {
            mConnected = true;
            //updateConnectionState("connected");
            //invalidateOptionsMenu();
            Log.d(TAG, "Action received: " + action);

            } else if (BluetoothLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState("disconnected");
                //clearUI();
            } else if (BluetoothLEService.ACTION_GATT_SERVICES_DISCOVERED.equals
                    (action)) {
                //displayGattServices(mBluetoothLEService.getSupportedGattServices());
            } else if (BluetoothLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLEService.EXTRA_DATA));
            }
        }
    };

    private static IntentFilter GattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    /*
    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATIO
                            N},
                    Constants.REQUEST_LOCATION_ENABLE_CODE);
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Your devices that don't support BLE", Toast.LENGTH_LONG).show();
            finish();
        }
        if (!mBluetoothAdapter.enable()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, Constants.REQUEST_BLUETOOTH_ENABLE_CODE);
        }
        registerReceiver(mGattUpdateReceiver, GattUpdateIntentFilter());
        if (mBluetoothLEService != null) {
            final boolean result = mBluetoothLEService.connect(bluetoothDevice.getAddress());
            Log.d(TAG, "Connect request result=" + result);
        }
    }
*/
    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //unbindService(mServiceConnection);
        mBluetoothLEService = null;
    }

    private void updateConnectionState(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //final TextView tvs = findViewById(R.id.deviceState);
                //tvs.setText(status);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            final TextView tvb = findViewById(R.id.textInfo);
            tvb.setText(data);
        }
    }

    /*
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;
        String uuid = null;
        String serviceString = "unknown service";
        String charaString = "unknown characteristic";

        for (BluetoothGattService gattService : gattServices) {

            uuid = gattService.getUuid().toString();
            Log.d(TAG, "Found service : " + uuid);

            serviceString = SampleGattAttributes.lookup(uuid);

            if (serviceString != null) {
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    HashMap<String, String> currentCharaData = new HashMap<String, String>();
                    uuid = gattCharacteristic.getUuid().toString();
                    Log.d(TAG, "Found characteristic : " + uuid);
                    charaString = SampleGattAttributes.lookup(uuid);
                    if (charaString != null) {
                        final TextView tvn = findViewById(R.id.serviceName);
                        tvn.setText(charaString);
                    }
                    mNotifyCharacteristic = gattCharacteristic;
                    return;
                }
            }
        }
    }
*/
}
