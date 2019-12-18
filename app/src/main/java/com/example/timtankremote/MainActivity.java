package com.example.timtankremote;

import androidx.appcompat.app.ActionBar;
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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final double battMin = 10.8;
    public static final double battMax = 12.6;
    public static final double battLow = 11.1;
    public static final double battHigh = 11.8;
    public static final int battMeterMax = 180;
    public static final int battK = 265;
    public static final int p5p6K = 1241;

    private static DecimalFormat df2 = new DecimalFormat(("#.##"));

    public static final int REQUEST_BLUETOOTH_ENABLE_CODE = 101;
    public static final int REQUEST_LOCATION_ENABLE_CODE = 101;
    public static final int SEARCH_REQUEST_CODE = 191;
    private static final String TAG = "MainActivity";

    private static final String fileNameString = "my_preferences";
    private static SharedPreferences sharedPreferences;
    private String mobAddress;
    private String mobName;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothLEService mBluetoothLEService;

    private boolean mBound = false;
    private boolean mConnected = false;
    private boolean mConnPaused = false;

    // Mobile device
    private class Motors {
        public String state;
        public int queueLen;
        public int vleft_set;
        public int vright_set;
        public int vleft_real;
        public int vright_real;
        public Motors() {
            state = "s";
            queueLen = vleft_real = vleft_set = vright_real = vright_set = 0;
        }
    }

    private class Servo {
        public int queueLen;
        public int angle_set;
        public int angle_real;
        public Servo() {
            queueLen = angle_real = angle_set = 0;
        }
    }

    private class Stepper {
        public int queueLen;
        public String state;
        public int angle_set;
        public int angle_real;
        public Stepper() {
            state = "s";
            queueLen = angle_real = angle_set = 0;
        }
    }

    private class Queues {
        public int common;
        public int motors;
        public int stepper;
        public int servo;
        public int dist;
        public int leds;
        public Queues() {
            common = motors = stepper = servo = dist = leds = 0;
        }
    }

    private Motors motors;
    private Servo servo;
    private Stepper stepper;
    private Queues queues;

    private int Leds[];
    private int dist = 0;
    private double v3v3 = 0;
    private double v6v = 0;
    private double vP5 = 0;
    private double vP6 = 0;

    private ActionBar actionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("   Управление танком");
        actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setIcon(R.drawable.ic_battery_alert_black_24dp);

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
        final Button but_find = findViewById(R.id.butFind);
        but_find.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                startActivityForResult(intent, SEARCH_REQUEST_CODE);
            }
        });
        but_find.setTextColor(Color.GREEN);


        final Button but_connect = (Button) findViewById(R.id.butConnect);
        but_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBluetoothLEService != null) {
                    if (!mConnected) {
                        registerReceiver(mGattUpdateReceiver, GattUpdateIntentFilter());
                        final boolean result = mBluetoothLEService.connect(mobAddress);
                        Log.d(TAG, "mBluetoothLEService - connect= " + result);
                    }
                    else {
                        mBluetoothLEService.disconnect();
                        unregisterReceiver(mGattUpdateReceiver);
                        mConnected = false;
                        updateConnectionState();
                        Log.d(TAG, "mBluetoothLEService - disconnect");
                    }
                } else {
                    Log.d(TAG, "mBluetoothLEService is null! ");
                }
            }
        });

        but_connect.setClickable(false);
        but_connect.setTextColor(Color.GRAY);

        Leds = new int[4];

        Context context = this;
        BluetoothManager bluetoothManager = (BluetoothManager)
                context.getSystemService(context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (!mBluetoothAdapter.enable()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_BLUETOOTH_ENABLE_CODE);
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
                but_connect.setTextColor(Color.GREEN);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent gattServiceIntent = new Intent(this, BluetoothLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mServiceConnection);
            mBound = false;
        }
    }
    // Result of SearchActivity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult");

        if ((requestCode == REQUEST_BLUETOOTH_ENABLE_CODE) &&
                (resultCode == RESULT_CANCELED)) {
            finish();
        }

        if ((requestCode == SEARCH_REQUEST_CODE) && (resultCode == RESULT_OK)) {
            //Retrieve data in the intent
            String addressValue = data.getStringExtra("ADDRESS");
            String nameValue = data.getStringExtra("NAME");
            final Button btc = findViewById(R.id.butConnect);
            Log.d(TAG, "onActivityResult: " + nameValue + ", " + addressValue);
            if (addressValue != null) {
                bluetoothDevice = mBluetoothAdapter.getRemoteDevice(addressValue);
                if (bluetoothDevice != null) {
                    final TextView tvl = findViewById(R.id.textLeName);
                    tvl.setText(nameValue);
                    final TextView tvi = findViewById(R.id.textInfo);
                    tvi.setText(addressValue);
                    // save device to the shared preferences
                    SharedPreferences.Editor editor =sharedPreferences.edit();
                    editor.putString("MOB_ADDRESS", addressValue);
                    editor.putString("MOB_NAME", nameValue);
                    editor.apply();
                    mobAddress = addressValue;
                    mobName = nameValue;
                    btc.setClickable(true);
                    btc.setTextColor(Color.GREEN);
                } else {
                    btc.setClickable(false);
                    btc.setTextColor(Color.GRAY);
                }
            }
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            BluetoothLEService.LocalBinder binder = (BluetoothLEService.LocalBinder) service;
            mBluetoothLEService = binder.getService();
            if (!mBluetoothLEService.initialize()) {
                Log.d(TAG, "Unable to initialize BluetoothLEService");
                finish();
            }
            else {
                mBound = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "BluetoothLEService disconnected");
            mBluetoothLEService = null;
            mBound = false;
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
            final Button btc = findViewById(R.id.butConnect);
            final Button btf = findViewById(R.id.butFind);
            Log.d(TAG, "Action received: " + action);
        if (BluetoothLEService.ACTION_GATT_CONNECTED.equals(action)) {
            mConnected = true;
            updateConnectionState();
        } else if (BluetoothLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
            mConnected = false;
            updateConnectionState();
        } else if (BluetoothLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //displayGattServices(mBluetoothLEService.getSupportedGattServices());
        } else if (BluetoothLEService.ACTION_DATA_AVAILABLE.equals(action)) {
            processData(intent.getStringExtra(BluetoothLEService.EXTRA_DATA));
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



    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_ENABLE_CODE);
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Your devices that don't support BLE",
                    Toast.LENGTH_LONG).show();
            finish();
        }
        if (!mBluetoothAdapter.enable()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_BLUETOOTH_ENABLE_CODE);
        }

        registerReceiver(mGattUpdateReceiver, GattUpdateIntentFilter());

        if (mConnPaused && (mBluetoothLEService != null)) {
            final boolean result = mBluetoothLEService.connect(bluetoothDevice.getAddress());
            mConnPaused = false;
            Log.d(TAG, "Reconnect to " + bluetoothDevice.getAddress() + ", result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mConnected) {
            unregisterReceiver(mGattUpdateReceiver);
            mConnPaused = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLEService = null;
    }

    private void updateConnectionState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Button btc = findViewById(R.id.butConnect);
                final Button btf = findViewById(R.id.butFind);
                if (mConnected) {
                    btc.setText("Disconnect");
                    btf.setClickable(false);
                    btf.setTextColor(Color.GRAY);
                } else {
                    btc.setText("Connect");
                    btf.setClickable(true);
                    btf.setTextColor(Color.GREEN);
                }
            }
        });
    }

    private void processData(String data) {
        if (data != null) {
            final TextView tvd = findViewById(R.id.textDebug);

            try {
                JSONObject jo = new JSONObject(data);
                JSONArray st;
                if (jo.has("adc")) {
                    st = jo.getJSONArray("adc");
                    int numadc =  Integer.parseInt(st.getString(0));
                    double valadc = Double.parseDouble(st.getString(1));
                    switch (numadc) {
                        case 0:
                            v3v3 = valadc / battK;
                            double perc = v3v3 / battMax;
                            int drawable;
                            if (perc > 0.9) {
                                drawable = R.drawable.ic_battery_90_black_24dp;
                            } else if (perc > 0.8) {
                                drawable = R.drawable.ic_battery_80_black_24dp;
                            } else if (perc > 0.6) {
                                drawable = R.drawable.ic_battery_60_black_24dp;
                            } else if (perc > 0.5) {
                                drawable = R.drawable.ic_battery_50_black_24dp;
                            } else if (perc > 0.3) {
                                drawable = R.drawable.ic_battery_30_black_24dp;
                            } else if (perc > 0.2) {
                                drawable = R.drawable.ic_battery_20_black_24dp;
                            } else {
                                drawable = R.drawable.ic_battery_alert_black_24dp;
                            }
                            actionBar.setIcon(drawable);
                            tvd.append("v3v3= " + df2.format(v3v3) + "\n");

                            /*
                            //tvd.append("v3v3= " + df2.format(v3v3));
                            int meterV = (int) Math.round((v3v3 - battMin) * 100);
                            if (meterV < 0) {
                                meterV = 0;
                            } else if (meterV > battMeterMax) {
                                meterV = battMeterMax;
                            }
                            ProgressBar pb = findViewById(R.id.progressBatt);
                            int col = Color.GREEN;
                            if (v3v3 < battLow) {
                                col = Color.RED;
                            } else if (v3v3 < battHigh) {
                                col = Color.YELLOW;
                            }
                            pb.setProgressTintList(ColorStateList.valueOf(col));
                            pb.setProgress(meterV);

                             */
                            break;
                        case 1:
                            v6v = valadc / battK;
                            //tvd.append("v6v= " + df2.format(v6v));
                            break;
                        case 2:
                            vP5 = valadc / p5p6K;
                            //tvd.append("vP5= " + df2.format(vP5));
                            break;
                        case 3:
                            vP6 = valadc / p5p6K;
                            //tvd.append("vP6= " + df2.format(vP6));
                    }
                } else if (jo.has("ms")) {
                    st = jo.getJSONArray("ms");
                    String s = st.getString(0);
                    if (s.equals("s") || s.equals("a")) {
                        motors.state = s;
                    } else if (s.equals("t") || s.equals("r") || s.equals("l")) {
                        motors.vleft_real = Integer.parseInt(st.getString(1));
                        motors.vright_real = Integer.parseInt(st.getString(2));
                    } else if (s.equals("f") || s.equals("b")) {
                        motors.vleft_real = motors.vright_real =
                                Integer.parseInt(st.getString(1));
                    }
                } else if (jo.has("sv")) {
                    st = jo.getJSONArray("sv");
                    int num = Integer.parseInt(st.getString(0));
                    if (num == 0) {
                        stepper.angle_real = Integer.parseInt(st.getString(2));
                    }
                } else if (jo.has("servo")) {
                    servo.angle_real = Integer.parseInt(jo.getString("servo"));
                } else if (jo.has("dist")) {
                    dist = Integer.parseInt(jo.getString("dist"));
                } else if (jo.has("leds")) {
                    st = jo.getJSONArray("leds");
                    int num = Integer.parseInt(st.getString(0));
                    switch (st.getString(1)) {
                        case "d":
                            Leds[num] = 0;
                            break;
                        case "u":
                            Leds[num] = 1;
                            break;
                        case "b":
                            Leds[num] = 2;
                    }
                } else if (jo.has("queue")) {
                    queues.common =  Integer.parseInt(jo.getString("queue"));
                } else if (jo.has("qmot")) {
                    queues.motors =  Integer.parseInt(jo.getString("qmot"));
                } else if (jo.has("qst1")) {
                    queues.stepper = Integer.parseInt(jo.getString("qst1"));
                } else if (jo.has("qdist")) {
                    queues.dist = Integer.parseInt(jo.getString("qdist"));
                } else if (jo.has("qleds")) {
                    queues.leds = Integer.parseInt(jo.getString("qleds"));
                } else if (jo.has("batt")) {
                    String s = jo.getString("batt");
                    if (s.equals("b")) {
                        Toast.makeText(this, "Внимание! Батарея разряжена!",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Внимание! Слишком большая нагрузка!",
                                Toast.LENGTH_LONG).show();
                    }
                } else if (jo.has("pwroff")) {
                    String s = jo.getString("pwroff");
                    if (s.equals("k")) {
                        Toast.makeText(this, "Устройство выключено по нажатию кнопки.",
                                Toast.LENGTH_LONG).show();
                    } else if (s.equals("с")) {
                        Toast.makeText(this, "Устройство выключено по команде пульта.",
                                Toast.LENGTH_LONG).show();
                    } else if (s.equals("b")) {
                        Toast.makeText(this, "Устройство выключено по разряду батареи.",
                                Toast.LENGTH_LONG).show();
                    }
                    mBluetoothLEService.disconnect();
                    unregisterReceiver(mGattUpdateReceiver);
                    mConnected = false;
                    updateConnectionState();
                    Log.d(TAG, "Power off - " + s);
                } else if (jo.has("dbg")) {
                    String s = jo.getString("dbg");
                    TextView tv = findViewById(R.id.textInfo);
                    tv.append(s);
                }
            } catch (JSONException e) {
                Log.d(TAG, "Cannot unpack JSON data");
            }
            //tvd.append(data);
        }
    }

}
