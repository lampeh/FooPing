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

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class PingService extends IntentService {
	private static final String tag = PingService.class.getSimpleName();

	private static final String prefix = PingService.class.getName().toLowerCase(Locale.ENGLISH);
	public static final String ACTION_ALL = prefix + ".action.all";
	public static final String ACTION_DEFAULT = prefix + ".action.default";
	public static final String ACTION_PING = prefix + ".action.ping";
	public static final String ACTION_BATTERY = prefix + ".action.battery";
	public static final String ACTION_GPS = prefix + ".action.gps";
	public static final String ACTION_NETWORK = prefix + ".action.network";
	public static final String ACTION_WIFI = prefix + ".action.wifi";
	public static final String ACTION_SENSORS = prefix + ".action.sensors";
	public static final String ACTION_CONN = prefix + ".action.conn";
	public static final String ACTION_GCM = prefix + ".action.gcm";
	public static final String ACTION_GPS_ACTIVE = prefix + ".action.gps_active";

	public static final String EXTRA_RECEIVER = prefix + ".extra.receiver";
	public static final String EXTRA_MSGLEN = prefix + ".extra.msglen";
	public static final String EXTRA_OUTPUT = prefix + ".extra.output";
	public static final String EXTRA_INTENT = prefix + ".extra.intent";
	public static final String EXTRA_RESULTS = prefix + ".extra.results";

	public static final String PERMISSION_IPC = "org.openchaos.android.fooping.permission.IPC";


	private static double roundValue(double value, int scale) {
		return BigDecimal.valueOf(value).setScale(scale, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().doubleValue();
	}


	private SharedPreferences prefs;
	private LocationManager lm;
	private WifiManager wm;
	private SensorManager sm;
	private ConnectivityManager cm;

	private boolean compress;
	private boolean encrypt;
	private SecretKeySpec cipherKey;
	private Cipher cipher;
	private SecretKeySpec macKey;
	private Mac mac;


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
		final String clientID = prefs.getString("ClientID", "unknown");

		String action = intent.getAction();
		if (action == null) {
			Log.e(tag, "Intent specifies no action. Request ignored");
			return;
		}

		final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
		if (receiver == null) {
			Log.e(tag, "Intent specifies no receiver. Request ignored");
			return;
		}

		ArrayList<Bundle> results = new ArrayList<Bundle>();

		Log.d(tag, "onHandleIntent(): " + action);

		// always send ping
		if (ACTION_PING.equals(action) || ACTION_ALL.equals(action) || (ACTION_DEFAULT.equals(action) && true)) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "ping");
				json.put("ts", ts);

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, "ACTION_PING failed", e);
			}
		}

		// http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
		// http://developer.android.com/reference/android/os/BatteryManager.html
		if (ACTION_BATTERY.equals(action) || ACTION_ALL.equals(action) || (ACTION_DEFAULT.equals(action) && prefs.getBoolean("UseBattery", false))) {
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
					if ((level >= 0) && (scale > 0)) {
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
				Log.e(tag, "ACTION_BATTERY failed", e);
			}
		}

		// http://developer.android.com/guide/topics/location/strategies.html
		// http://developer.android.com/reference/android/location/LocationManager.html
		if (ACTION_GPS.equals(action) || ACTION_ALL.equals(action) || (ACTION_DEFAULT.equals(action) && prefs.getBoolean("UseGPS", false))) {
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
				Log.e(tag, "ACTION_GPS failed", e);
			}
		}

		if (ACTION_NETWORK.equals(action) || ACTION_ALL.equals(action) || (ACTION_DEFAULT.equals(action) && prefs.getBoolean("UseNetwork", false))) {
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
				Log.e(tag, "ACTION_NETWORK failed", e);
			}
		}

		// http://developer.android.com/reference/android/net/wifi/WifiManager.html
		if (ACTION_WIFI.equals(action) || ACTION_ALL.equals(action) || (ACTION_DEFAULT.equals(action) && prefs.getBoolean("UseWIFI", false))) {
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
				Log.e(tag, "ACTION_WIFI failed", e);
			}
		}

		// TODO: cannot poll sensors. register receiver to cache sensor data
		// http://developer.android.com/guide/topics/sensors/sensors_overview.html
		// http://developer.android.com/reference/android/hardware/SensorManager.html
		if (ACTION_SENSORS.equals(action) || ACTION_ALL.equals(action) || (ACTION_DEFAULT.equals(action) && prefs.getBoolean("UseSensors", false))) {
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
				Log.e(tag, "ACTION_SENSORS failed", e);
			}
		}

		// http://developer.android.com/training/monitoring-device-state/connectivity-monitoring.html
		// http://developer.android.com/reference/android/net/ConnectivityManager.html
		if (ACTION_CONN.equals(action) || ACTION_ALL.equals(action) || (ACTION_DEFAULT.equals(action) && prefs.getBoolean("UseConn", false))) {
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
				Log.e(tag, "ACTION_CONN failed", e);
			}
		}

		if (ACTION_GCM.equals(action) || ACTION_ALL.equals(action) || (ACTION_DEFAULT.equals(action) && prefs.getBoolean("EnableGCM", false))) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "gcm");
				json.put("ts", ts);
				json.put("gcm_id", prefs.getString("GCM_ID", ""));

				results.add(prepareMessage(json));
			} catch (Exception e) {
				Log.e(tag, "ACTION_GCM failed", e);
			}
		}

		// XXX: work in progress. weird code, much redundant
		if (ACTION_GPS_ACTIVE.equals(action) || ACTION_ALL.equals(action)) {
			// TODO: keep a list of active requests, use single LocationListener
			// XXX: complicated way to remove the LocationListener through AlarmManager
			// Cannot use handler schedule because it doesn't wake from sleep
			// Cannot target anonymous BroadcastReceiver instances directly
			// We also need the final intent in the listener before the receiver exists
			// Use unique intent action, package restriction and permission instead
			final String reqId = prefix + ":cancel-" + UUID.randomUUID().toString();
			final Intent reqIntent = new Intent(reqId);
			reqIntent.setPackage(getPackageName());
			reqIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_REPLACE_PENDING);

			final Context appContext = getApplicationContext();

			if (lm == null) {
				lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
			}

			// XXX: every request spawns its own LocationListener, BroadcastReceiver and alarms
			final LocationListener locationUpdate = new LocationListener() {
				private int numFixes = 0;
				private PendingIntent cancelIntent;

				@Override
				public void onLocationChanged(final Location location) {
					Log.d(tag, "LocationListener: onLocationChanged()");

					if (location == null) {
						return;
					}

					if (numFixes++ == 0) {
						// send location updates for up to XXX seconds after first fix
						cancelIntent = PendingIntent.getBroadcast(appContext, 0, reqIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
						((AlarmManager)getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (30 * 1000), cancelIntent);
					} else if (numFixes >= 15) {
						// send up to XXX location updates
						try {
							cancelIntent.send();
						} catch (Exception e) {
							Log.e(tag, "Cancel failed", e);
						}
					}

					// listener runs on main thread, return results in background
					new AsyncTask<Void, Void, Void>() {
						@Override
						protected Void doInBackground(Void... params) {
							try {
								JSONObject json = new JSONObject();
								json.put("client", clientID);
								json.put("type", "loc_gps");
								json.put("ts", System.currentTimeMillis());

								JSONObject loc_data = new JSONObject();
								loc_data.put("ts", location.getTime());
								loc_data.put("lat", location.getLatitude());
								loc_data.put("lon",  location.getLongitude());
								if (location.hasAltitude()) loc_data.put("alt", roundValue(location.getAltitude(), 4));
								if (location.hasAccuracy()) loc_data.put("acc", roundValue(location.getAccuracy(), 4));
								if (location.hasSpeed()) loc_data.put("speed", roundValue(location.getSpeed(), 4));
								if (location.hasBearing()) loc_data.put("bearing", roundValue(location.getBearing(), 4));
								json.put("loc_gps", loc_data);

								ArrayList<Bundle> results = new ArrayList<Bundle>();
								results.add(prepareMessage(json));

								// return data through ResultReceiver
								Bundle resultData = new Bundle();
								resultData.putParcelableArrayList(EXTRA_RESULTS, results);
								receiver.send(0, resultData);
							} catch (Exception e) {
								Log.e(tag, "ACTION_GPS_ACTIVE failed", e);
							}
							return null;
						}
					}.execute();
				}

				@Override
				public void onProviderDisabled(String provider) {
				}

				@Override
				public void onProviderEnabled(String provider) {
				}

				@Override
				public void onStatusChanged(String provider, int status, Bundle extras) {
				}
			};

			// receive broadcast from AlarmManager and remove this LocationListener instance
			appContext.registerReceiver(new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
						Log.d(tag, "Removing LocationListener (reqId " + reqId + ")");
						lm.removeUpdates(locationUpdate);
						context.unregisterReceiver(this);
					}
				}, new IntentFilter(reqId), PERMISSION_IPC, null);

			// start location updates
			Log.d(tag, "Adding LocationListener (reqId " + reqId + ")");
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationUpdate, getMainLooper());

			// wait up to XXX seconds for first fix
			((AlarmManager)getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (120 * 1000),
					PendingIntent.getBroadcast(appContext, 0, reqIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT));
		}

		Bundle resultData = new Bundle();
		resultData.putParcelable(EXTRA_INTENT, intent);
		resultData.putParcelableArrayList(EXTRA_RESULTS, results);
		// TODO: set meaningful result code
		receiver.send(0, resultData);
	}

	private Bundle prepareMessage (final JSONObject json) {
		if (encrypt) {
			if (cipherKey == null) {
				try {
					cipherKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
							.digest(prefs.getString("ExchangeKey", null).getBytes("US-ASCII")), "AES");
				} catch (Exception e) {
					Log.e(tag, "Failed to set cipher key", e);
					return null;
				}
			}

			if (macKey == null) {
				try {
					macKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
							.digest(prefs.getString("MacKey", null).getBytes("US-ASCII")), "HmacSHA1");
				} catch (Exception e) {
					Log.e(tag, "Failed to set mac key", e);
					return null;
				}
			}

			// TODO: use GCM or other AE(AD) mode (requires PyCrypto 2.7+ on the server)
			// TODO: use associated data for flags and message counter
			if (cipher == null) {
				try {
					cipher = Cipher.getInstance("AES/CFB8/NoPadding");
				} catch (Exception e) {
					Log.e(tag, "Failed to get cipher instance", e);
					return null;
				}
			}

			if (mac == null) {
				try {
					mac = Mac.getInstance("HmacSHA1");
					mac.init(macKey);
				} catch (Exception e) {
					Log.e(tag, "Failed to get mac instance", e);
					return null;
				}
			}
		}

		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			CipherOutputStream cos = null;
			GZIPOutputStream zos = null;

			final byte[] message = new JSONArray().put(json).toString().getBytes();

			// TODO: send protocol header to signal compression & encryption

			if (encrypt) {
				cipher.init(Cipher.ENCRYPT_MODE, cipherKey);
				cos = new CipherOutputStream(baos, cipher);

				// write iv block
				baos.write(cipher.getIV());
			}

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

			// append HMAC tag
			baos.write(mac.doFinal(baos.toByteArray()));

			final byte[] output = baos.toByteArray();
			baos.close();

			Bundle resultData = new Bundle();
			resultData.putByteArray(EXTRA_OUTPUT, output);
			resultData.putLong(EXTRA_MSGLEN, message.length);
			return resultData;
		} catch (Exception e) {
			Log.e(tag, "Failed to write message", e);
			return null;
		}
	}
}
