/*
 * Copyright 2014 Hauke Lampe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */


package org.openchaos.android.fooping.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Log;


public class PingService extends IntentService {
	private static final String tag = "PingService";

	public static final String ACTION_ALL = "org.openchaos.android.fooping.action.all";
	public static final String ACTION_DEFAULT = "org.openchaos.android.fooping.action.default";
	public static final String ACTION_PING = "org.openchaos.android.fooping.action.ping";
	public static final String ACTION_BATTERY = "org.openchaos.android.fooping.action.battery";
	public static final String ACTION_GPS = "org.openchaos.android.fooping.action.gps";
	public static final String ACTION_NETWORK = "org.openchaos.android.fooping.action.network";
	public static final String ACTION_WIFI = "org.openchaos.android.fooping.action.wifi";
	public static final String ACTION_SENSORS = "org.openchaos.android.fooping.action.sensors";
	public static final String ACTION_CONN = "org.openchaos.android.fooping.action.conn";
	public static final String ACTION_GCM = "org.openchaos.android.fooping.action.gcm";

	public static final String EXTRA_RECEIVER = "org.openchaos.android.fooping.extra.receiver";
	public static final String EXTRA_MSGLEN = "org.openchaos.android.fooping.extra.msglen";
	public static final String EXTRA_OUTPUT = "org.openchaos.android.fooping.extra.output";
	public static final String EXTRA_INTENT = "org.openchaos.android.fooping.extra.intent";
	public static final String EXTRA_RESULTS = "org.openchaos.android.fooping.extra.results";

	private SharedPreferences prefs;
	private LocationManager lm;
	private WifiManager wm;
	private SensorManager sm;
	private ConnectivityManager cm;

	private boolean compress;
	private boolean encrypt;
	private SecretKeySpec skeySpec;
	private Cipher cipher;

	private static final double roundValue(double value, int scale) {
		return BigDecimal.valueOf(value).setScale(scale, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().doubleValue();
	}

	public PingService() {
		super(tag);
	}

	@Override
	public void onCreate() {
		Log.d(tag, "onCreate()");
		super.onCreate();

		// NB: DefaultSharedPreferences only works if the service runs in
		// the same process as the activity with the PreferenceFragment
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		encrypt = prefs.getBoolean("SendAES", false);
		compress = prefs.getBoolean("SendGZIP", false);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		long ts = System.currentTimeMillis();
		String clientID = prefs.getString("ClientID", "unknown");
		ArrayList<Bundle> results = new ArrayList<Bundle>();

		String action = intent.getAction();
		if (action == null) {
			Log.e(tag, "Intent specifies no action, assuming ACTION_DEFAULT");
			action = ACTION_DEFAULT;
		}

		Log.d(tag, "onHandleIntent(): " + action);

		// always send ping
		if (action == ACTION_PING || action == ACTION_ALL || (action == ACTION_DEFAULT && true)) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "ping");
				json.put("ts", ts);

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		// http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
		// http://developer.android.com/reference/android/os/BatteryManager.html
		if (action == ACTION_BATTERY || action == ACTION_ALL || (action == ACTION_DEFAULT && prefs.getBoolean("UseBattery", false))) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "battery");
				json.put("ts", ts);

				Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
				if (batteryStatus != null) {
					JSONObject bat_data = new JSONObject();

					int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
					int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
					if (level >= 0 && scale > 0) {
						bat_data.put("pct", roundValue(((double)level / (double)scale)*100, 2));
					} else {
						Log.w(tag, "Battery level unknown");
						bat_data.put("pct", -1);
					}
					bat_data.put("health", batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));
					bat_data.put("status", batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
					bat_data.put("plug", batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
					bat_data.put("volt", batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
					bat_data.put("temp", batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
					bat_data.put("tech", batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
					// bat_data.put("present", batteryStatus.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false));

					json.put("battery", bat_data);
				}

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		// http://developer.android.com/guide/topics/location/strategies.html
		// http://developer.android.com/reference/android/location/LocationManager.html
		if (action == ACTION_GPS || action == ACTION_ALL || (action == ACTION_DEFAULT && prefs.getBoolean("UseGPS", false))) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "loc_gps");
				json.put("ts", ts);

				if (lm == null) {
					lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
				}

				Location last_loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				if (last_loc != null) {
					JSONObject loc_data = new JSONObject();

					loc_data.put("ts", last_loc.getTime());
					loc_data.put("lat", last_loc.getLatitude());
					loc_data.put("lon",  last_loc.getLongitude());
					if (last_loc.hasAltitude()) loc_data.put("alt", roundValue(last_loc.getAltitude(), 4));
					if (last_loc.hasAccuracy()) loc_data.put("acc", roundValue(last_loc.getAccuracy(), 4));
					if (last_loc.hasSpeed()) loc_data.put("speed", roundValue(last_loc.getSpeed(), 4));
					if (last_loc.hasBearing()) loc_data.put("bearing", roundValue(last_loc.getBearing(), 4));

					json.put("loc_gps", loc_data);
				}

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		if (action == ACTION_NETWORK || action == ACTION_ALL || (action == ACTION_DEFAULT && prefs.getBoolean("UseNetwork", false))) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "loc_net");
				json.put("ts", ts);

				if (lm == null) {
					lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
				}

				Location last_loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				if (last_loc != null) {
					JSONObject loc_data = new JSONObject();

					loc_data.put("ts", last_loc.getTime());
					loc_data.put("lat", last_loc.getLatitude());
					loc_data.put("lon",  last_loc.getLongitude());
					if (last_loc.hasAltitude()) loc_data.put("alt", roundValue(last_loc.getAltitude(), 4));
					if (last_loc.hasAccuracy()) loc_data.put("acc", roundValue(last_loc.getAccuracy(), 4));
					if (last_loc.hasSpeed()) loc_data.put("speed", roundValue(last_loc.getSpeed(), 4));
					if (last_loc.hasBearing()) loc_data.put("bearing", roundValue(last_loc.getBearing(), 4));

					json.put("loc_net", loc_data);
				}

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		// http://developer.android.com/reference/android/net/wifi/WifiManager.html
		if (action == ACTION_WIFI || action == ACTION_ALL || (action == ACTION_DEFAULT && prefs.getBoolean("UseWIFI", false))) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "wifi");
				json.put("ts", ts);

				if (wm == null) {
					wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
				}

				List<ScanResult> wifiScan = wm.getScanResults();
				if (wifiScan != null) {
					JSONArray wifi_list = new JSONArray(); 

					for (ScanResult wifi : wifiScan) {
						JSONObject wifi_data = new JSONObject();

						wifi_data.put("BSSID", wifi.BSSID);
						wifi_data.put("SSID", wifi.SSID);
						wifi_data.put("freq", wifi.frequency);
						wifi_data.put("level", wifi.level);
						// wifi_data.put("cap", wifi.capabilities);
						// wifi_data.put("ts", wifi.timestamp);

						wifi_list.put(wifi_data);
					}

					json.put("wifi", wifi_list);
				}

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		// TODO: cannot poll sensors. register receiver to cache sensor data
		// http://developer.android.com/guide/topics/sensors/sensors_overview.html
		// http://developer.android.com/reference/android/hardware/SensorManager.html
		if (action == ACTION_SENSORS || action == ACTION_ALL || (action == ACTION_DEFAULT && prefs.getBoolean("UseSensors", false))) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "sensors");
				json.put("ts", ts);

				if (sm == null) {
					sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
				}

				List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
				if (sensors != null) {
					JSONArray sensor_list = new JSONArray();

					for (Sensor sensor : sensors) {
						JSONObject sensor_info = new JSONObject();

						sensor_info.put("name", sensor.getName());
						sensor_info.put("type", sensor.getType());
						sensor_info.put("vendor", sensor.getVendor());
						sensor_info.put("version", sensor.getVersion());
						sensor_info.put("power", roundValue(sensor.getPower(), 4));
						sensor_info.put("resolution", roundValue(sensor.getResolution(), 4));
						sensor_info.put("range", roundValue(sensor.getMaximumRange(), 4));

						sensor_list.put(sensor_info);
					}

					json.put("sensors", sensor_list);
				}

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		// http://developer.android.com/training/monitoring-device-state/connectivity-monitoring.html
		// http://developer.android.com/reference/android/net/ConnectivityManager.html
		if (action == ACTION_CONN || action == ACTION_ALL || (action == ACTION_DEFAULT && prefs.getBoolean("UseConn", false))) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "conn");
				json.put("ts", ts);

				if (cm == null) {
					cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
				}

				// TODO: add active/all preferences below UseConn
				if (prefs.getBoolean("UseConnActive", true)) {
					NetworkInfo net = cm.getActiveNetworkInfo();
					if (net != null) {
						JSONObject net_data = new JSONObject();

						net_data.put("type", net.getTypeName());
						net_data.put("subtype", net.getSubtypeName());
						net_data.put("connected", net.isConnected());
						net_data.put("available", net.isAvailable());
						net_data.put("roaming", net.isRoaming());
						net_data.put("failover", net.isFailover());
						if (net.getReason() != null) net_data.put("reason", net.getReason());
						if (net.getExtraInfo() != null) net_data.put("extra", net.getExtraInfo());

						json.put("conn_active", net_data);
					}
				}

				if (prefs.getBoolean("UseConnAll", false)) {
					NetworkInfo[] nets = cm.getAllNetworkInfo();
					if (nets != null) {
						JSONArray net_list = new JSONArray(); 

						for (NetworkInfo net : nets) {
							JSONObject net_data = new JSONObject();

							net_data.put("type", net.getTypeName());
							net_data.put("subtype", net.getSubtypeName());
							net_data.put("connected", net.isConnected());
							net_data.put("available", net.isAvailable());
							net_data.put("roaming", net.isRoaming());
							net_data.put("failover", net.isFailover());
							if (net.getReason() != null) net_data.put("reason", net.getReason());
							if (net.getExtraInfo() != null) net_data.put("extra", net.getExtraInfo());

							net_list.put(net_data);
						}

						json.put("conn_all", net_list);
					}
				}

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		if (action == ACTION_GCM || action == ACTION_ALL || (action == ACTION_DEFAULT && prefs.getBoolean("EnableGCM", false))) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "gcm");
				json.put("ts", ts);
				json.put("gcm_id", prefs.getString("GCM_ID", ""));

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		if (!results.isEmpty()) {
			Bundle resultData = new Bundle();
			resultData.putParcelable(EXTRA_INTENT, intent);
			resultData.putParcelableArrayList(EXTRA_RESULTS, results);

			ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
			receiver.send(0, resultData);
		}
	}

	private Bundle prepareMessage (final JSONObject json) {
		if (encrypt) {
			if (skeySpec == null) {
				try {
					skeySpec = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
							.digest(prefs.getString("ExchangeKey", null).getBytes("US-ASCII")), "AES");
				} catch (Exception e) {
					Log.e(tag, e.toString());
					e.printStackTrace();
				}
			}

			if (cipher == null) {
				try {
					cipher = Cipher.getInstance("AES/CFB8/NoPadding");
				} catch (Exception e) {
					Log.e(tag, e.toString());
					e.printStackTrace();
				}
			}

			if (skeySpec == null || cipher == null) {
				Log.e(tag, "Encryption requested but not available");
				throw new AssertionError();
			}
		}

		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			CipherOutputStream cos = null;
			GZIPOutputStream zos = null;

			// TODO: send protocol header to signal compression & encryption

			if (encrypt) {
				cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
				cos = new CipherOutputStream(baos, cipher);

				// write iv block
				baos.write(cipher.getIV());
			}

			final byte[] message = new JSONArray().put(json).toString().getBytes();

			if (compress) {
				zos = new GZIPOutputStream((encrypt)?(cos):(baos));
				zos.write(message);
				zos.finish();
				zos.close();
				if (encrypt) {
					cos.close();
				}
			} else if (encrypt) {
				cos.write(message);
				cos.close();
			} else {
				baos.write(message);
			}

			baos.flush();
			final byte[] output = baos.toByteArray();
			baos.close();

			Bundle resultData = new Bundle();
			resultData.putByteArray(EXTRA_OUTPUT, output);
			resultData.putLong(EXTRA_MSGLEN, message.length);
			return resultData;
		} catch (Exception e) {
			Log.e(tag, e.toString());
			e.printStackTrace();
			return null;
		}
	}
}
