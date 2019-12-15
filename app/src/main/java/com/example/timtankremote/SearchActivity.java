package com.example.timtankremote;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.provider.SyncStateContract;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private static final String TAG = "SearchActivity";
    private static final String fileNameString = "my_preferences";

    private static SharedPreferences sharedPreferences;
    private String mobAddress;

    BluetoothDevice bluetoothDevice;
    private boolean mScanning;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private boolean mConnected = false;

    ListView listViewLE;

    List<BluetoothDevice> listBluetoothDevice;
    ListAdapter adapterLeScanResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Context context = SearchActivity.this;
        BluetoothManager bluetoothManager = (BluetoothManager)
                context.getSystemService(context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // button Start Scan
        final Button but_search = (Button) findViewById(R.id.butSearch);
        but_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                startScanning(true);
            }
        });

        final Button but_exit = (Button) findViewById(R.id.butExit);
        but_exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                setResult(RESULT_CANCELED, intent);
                SearchActivity.this.finish();
            }
        });

        listViewLE = (ListView)findViewById(R.id.devListView);

        listBluetoothDevice = new ArrayList<>();
        adapterLeScanResult = new ArrayAdapter<BluetoothDevice>(
                this,android.R.layout.simple_list_item_1, listBluetoothDevice);
        listViewLE.setAdapter(adapterLeScanResult);
        listViewLE.setOnItemClickListener(scanResultOnItemClickListener);

    }

    AdapterView.OnItemClickListener scanResultOnItemClickListener =
            new AdapterView.OnItemClickListener(){

                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    if (mScanning) {
                        return;
                    }

                    final BluetoothDevice device =
                            (BluetoothDevice) parent.getItemAtPosition(position);

                    String msg = device.getAddress() + "\n"
                            + device.getBluetoothClass().toString() + "\n"
                            + getBTDeviceType(device);

                    new AlertDialog.Builder(SearchActivity.this)
                            .setTitle(device.getName())
                            .setMessage(msg)
                            .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent();
                                    intent.putExtra("ADDRESS", device.getAddress());
                                    intent.putExtra("NAME", device.getName());
                                    setResult(RESULT_OK, intent);
                                    SearchActivity.this.finish();
                                }
                            })
                            .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                }
            };

    private String getBTDeviceType(BluetoothDevice d){
        String type = "";

        switch (d.getType()){
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                type = "DEVICE_TYPE_CLASSIC";
                break;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                type = "DEVICE_TYPE_DUAL";
                break;
            case BluetoothDevice.DEVICE_TYPE_LE:
                type = "DEVICE_TYPE_LE";
                break;
            case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                type = "DEVICE_TYPE_UNKNOWN";
                break;
            default:
                type = "unknown...";
        }

        return type;
    }

    private void setLocalViews(boolean scan) {
        findViewById(R.id.butSearch).setClickable(!scan);
        findViewById(R.id.butExit).setClickable(!scan);
        TextView ts = findViewById(R.id.textScan);
        if (scan) {
            ts.setText(R.string.scan_proc);
        } else {
            ts.setText(R.string.scan_fin);
        }
    }

    private void startScanning(final boolean enable) {
        bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        Handler mHandler = new Handler();
        if (enable) {
            //findViewById(R.id.butSearch).setClickable(false);
            //findViewById(R.id.butExit).setClickable(false);
            List<ScanFilter> scanFilters = new ArrayList<>();
            final ScanSettings settings = new ScanSettings.Builder().build();
/*
            ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(
                    ParcelUuid.fromString(SampleGattAttributes.UUID_????
            scanFilters.add(scanFilter);
*/
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
                    bluetoothLeScanner.stopScan(scanCallback);
                    //findViewById(R.id.butSearch).setClickable(true);
                    //findViewById(R.id.butExit).setClickable(true);
                    setLocalViews(mScanning);
                }
            }, Constants.SCAN_PERIOD);
            mScanning = true;
            setLocalViews(mScanning);
            bluetoothLeScanner.startScan(scanFilters, settings, scanCallback);
        } else {
            mScanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            //findViewById(R.id.butSearch).setClickable(true);
            //findViewById(R.id.butExit).setClickable(true);
            setLocalViews(mScanning);

        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            addBluetoothDevice(result.getDevice());
         }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for(ScanResult result : results){
                addBluetoothDevice(result.getDevice());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG, "Scanning Failed " + errorCode);
            findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
            //findViewById(R.id.butSearch).setClickable(true);
            //findViewById(R.id.butExit).setClickable(true);
            mScanning = false;
            setLocalViews(mScanning);
        }

        private void addBluetoothDevice(BluetoothDevice device){
            if(!listBluetoothDevice.contains(device)){
                String devt = getBTDeviceType(device);
                // adding LE supported devices only
                if ((devt == "DEVICE_TYPE_LE") || (devt == "DEVICE_TYPE_DUAL")) {
                    listBluetoothDevice.add(device);
                    listViewLE.invalidateViews();
                }
            }
        }

    };

}
