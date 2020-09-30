package info.nightscout.androidaps.plugins.pump.common.dialog;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.pump.common.R;
import info.nightscout.androidaps.plugins.pump.common.ble.BlePreCheck;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)

/**
 * Created by dirceu on 9/20/20
 */
public class MedLinkBLEScanActivity extends NoSplashAppCompatActivity {

    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;
    @Inject RxBusWrapper rxBus;
    @Inject ResourceHelper resourceHelper;
    @Inject BlePreCheck blePrecheck;
    @Inject MedLinkUtil medLinkUtil;
    @Inject ActivePluginProvider activePlugin;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 30241; // arbitrary.
    private static final int REQUEST_ENABLE_BT = 30242; // arbitrary

    private static String TAG = "MedLinkBLEScanActivity";

    // Stops scanning after 30 seconds.
    private static final long SCAN_PERIOD = 30000;
    public boolean mScanning;
    public ScanSettings settings;
    public List<ScanFilter> filters;
    public ListView listBTScan;
    public Toolbar toolbarBTScan;
    public Context mContext = this;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private Handler mHandler;

    private String actionTitleStart, actionTitleStop;
    private MenuItem menuItem;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.medlink_scan_activity);

        // Initializes Bluetooth adapter.
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler();

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        listBTScan = findViewById(R.id.medlink_listBTScan);
        listBTScan.setAdapter(mLeDeviceListAdapter);
        listBTScan.setOnItemClickListener((parent, view, position, id) -> {

            // stop scanning if still active
            if (mScanning) {
                mScanning = false;
                mLEScanner.stopScan(mScanCallback2);
            }

            TextView textview = view.findViewById(R.id.rileylink_device_address);
            String bleAddress = textview.getText().toString();

            sp.putString(MedLinkConst.Prefs.MedLinkAddress, bleAddress);

            PumpInterface activePump = activePlugin.getActivePump();

            if (activePump.manufacturer() == ManufacturerType.Medtronic) {
                RileyLinkPumpDevice rileyLinkPump = (RileyLinkPumpDevice) activePump;
                rileyLinkPump.getRileyLinkService().verifyConfiguration(); // force reloading of address

                rileyLinkPump.triggerPumpConfigurationChangedEvent();

            } else if (activePlugin.getActivePump().manufacturer() == ManufacturerType.Insulet) {
                if (activePump.model() == PumpType.Insulet_Omnipod_Dash) {
                    aapsLogger.error("Omnipod Dash not yet implemented.");
                } else {
                    RileyLinkPumpDevice rileyLinkPump = (RileyLinkPumpDevice) activePump;
                    rileyLinkPump.getRileyLinkService().verifyConfiguration(); // force reloading of address

                    rileyLinkPump.triggerPumpConfigurationChangedEvent();
                }
            }

            finish();
        });

        toolbarBTScan = findViewById(R.id.rileylink_toolbarBTScan);
        toolbarBTScan.setTitle(R.string.medlink_scanner_title);
        setSupportActionBar(toolbarBTScan);

        prepareForScanning();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_medlink_ble_scan, menu);

        actionTitleStart = resourceHelper.gs(R.string.rileylink_scanner_scan_scan);
        actionTitleStop = resourceHelper.gs(R.string.rileylink_scanner_scan_stop);

        menuItem = menu.getItem(0);

        menuItem.setTitle(actionTitleStart);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().equals(actionTitleStart)) {
            scanLeDevice(true);
            return true;
        } else if (item.getTitle().equals(actionTitleStop)) {
            scanLeDevice(false);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }


    public void prepareForScanning() {
        boolean checkOK = blePrecheck.prerequisitesCheck(this);

        if (checkOK) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
            aapsLogger.debug("filtering ble");
            filters = buildFilters();

        }

        // disable currently selected RL, so that we can discover it
        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.RileyLinkDisconnect, this);
    }

    private List<ScanFilter> buildFilters() {

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setDeviceName("MED-LINK").build());
        filters.add(new ScanFilter.Builder().setDeviceName("MED-LINK-2").build());
        filters.add(new ScanFilter.Builder().setDeviceName("MED-LINK-3").build());
        filters.add(new ScanFilter.Builder().setDeviceName("MMSoft").build());
        return filters;
    }


    private ScanCallback mScanCallback2 = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, final ScanResult scanRecord) {

            Log.d(TAG, scanRecord.toString());
            aapsLogger.info(LTag.APS,"scan results");
            runOnUiThread(() -> {
                if (addDevice(scanRecord))
                    mLeDeviceListAdapter.notifyDataSetChanged();
            });
        }


        @Override
        public void onBatchScanResults(final List<ScanResult> results) {
            Log.d(TAG, String.join(",", results.stream().map(ScanResult::toString).collect(Collectors.toList())));
            aapsLogger.info(LTag.APS,"bach scan results");
            runOnUiThread(() -> {

                boolean added = false;

                for (ScanResult result : results) {
                    if (addDevice(result))
                        added = true;
                }
                aapsLogger.info(LTag.APS,"result size "+ String.join(",", results.stream().map(ScanResult::toString).collect(Collectors.toList())));
                Toast.makeText(mContext, "result size"+ String.join(",", results.stream().map(ScanResult::toString).collect(Collectors.toList())), Toast.LENGTH_SHORT).show();
                if (added)
                    mLeDeviceListAdapter.notifyDataSetChanged();
            });
        }


        private boolean addDevice(ScanResult result) {

            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName().trim();
            if (deviceName.equals("MED-LINK") || deviceName.equals("MED-LINK-2") ||
                    deviceName.equals("MED-LINK-3") || deviceName.equals("HMSoft")) {
                Log.i(TAG, "Found Medlink with address: " + device.getAddress());
                Toast.makeText(mContext, deviceName, Toast.LENGTH_SHORT).show();
                mLeDeviceListAdapter.addDevice(result);
                return true;
            } else {
                Log.v(TAG, "Device " + device.getAddress() + " has incorrect uuid (Not RileyLink).");
            }

            return false;
        }


        private String getDeviceDebug(BluetoothDevice device) {
            return "BluetoothDevice [name=" + device.getName() + ", address=" + device.getAddress() + //
                    ", type=" + device.getType(); // + ", alias=" + device.getAlias();
        }


        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
            Toast.makeText(mContext, resourceHelper.gs(R.string.rileylink_scanner_scanning_error, errorCode),
                    Toast.LENGTH_LONG).show();
        }

    };


    private void scanLeDevice(final boolean enable) {

        if (mLEScanner == null)
            return;

        if (enable) {

            mLeDeviceListAdapter.clear();
            mLeDeviceListAdapter.notifyDataSetChanged();

            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(() -> {

                if (mScanning) {
                    mScanning = false;
                    mLEScanner.stopScan(mScanCallback2);
                    aapsLogger.debug("scanLeDevice: Scanning Stop");
                    Toast.makeText(mContext, R.string.rileylink_scanner_scanning_finished, Toast.LENGTH_SHORT).show();
                    menuItem.setTitle(actionTitleStart);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mLEScanner.startScan(filters, settings, mScanCallback2);
            aapsLogger.debug("scanLeDevice: Scanning Start");
            Toast.makeText(this, R.string.rileylink_scanner_scanning, Toast.LENGTH_SHORT).show();

            menuItem.setTitle(actionTitleStop);

        } else {
            if (mScanning) {
                mScanning = false;
                mLEScanner.stopScan(mScanCallback2);

                aapsLogger.debug("scanLeDevice: Scanning Stop 2");
                Toast.makeText(this, R.string.rileylink_scanner_scanning_finished, Toast.LENGTH_SHORT).show();

                menuItem.setTitle(actionTitleStart);
            }
        }
    }

    private class LeDeviceListAdapter extends BaseAdapter {

        private ArrayList<BluetoothDevice> mLeDevices;
        private Map<BluetoothDevice, Integer> medLinkDevices;
        private LayoutInflater mInflator;
        String currentlySelectedAddress;


        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            medLinkDevices = new HashMap<>();
            mInflator = MedLinkBLEScanActivity.this.getLayoutInflater();
            currentlySelectedAddress = sp.getString(MedLinkConst.Prefs.MedLinkAddress, "");
        }


        public void addDevice(ScanResult result) {

            if (!mLeDevices.contains(result.getDevice())) {
                mLeDevices.add(result.getDevice());
            }
            medLinkDevices.put(result.getDevice(), result.getRssi());
            notifyDataSetChanged();
        }


        public void clear() {
            mLeDevices.clear();
            medLinkDevices.clear();
            notifyDataSetChanged();
        }


        @Override
        public int getCount() {
            return mLeDevices.size();
        }


        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }


        @Override
        public long getItemId(int i) {
            return i;
        }


        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.rileylink_scan_item, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view.findViewById(R.id.rileylink_device_address);
                viewHolder.deviceName = view.findViewById(R.id.rileylink_device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            String deviceName = device.getName();

            if (StringUtils.isBlank(deviceName)) {
                deviceName = "MedLink";
            }

            deviceName += " [" + medLinkDevices.get(device).intValue() + "]";

            if (currentlySelectedAddress.equals(device.getAddress())) {
                // viewHolder.deviceName.setTextColor(getColor(R.color.secondary_text_light));
                // viewHolder.deviceAddress.setTextColor(getColor(R.color.secondary_text_light));
                deviceName += " (" + getResources().getString(R.string.rileylink_scanner_selected_device) + ")";
            }

            viewHolder.deviceName.setText(deviceName);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }

    }

    static class ViewHolder {

        TextView deviceName;
        TextView deviceAddress;
    }

}
