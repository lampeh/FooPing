/*
 * Copyright 2014 Hauke Lampe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */


package org.openchaos.android.fooping.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
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
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;


public class PingService extends IntentService {
	private static final String tag = "PingService";

	private SharedPreferences prefs;
	private LocationManager lm;
	private WifiManager wm;
	private SensorManager sm;

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
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String clientID = prefs.getString("ClientID", "unknown");
		long ts = System.currentTimeMillis();

		// always send ping
		if (true) {
			try {
				JSONObject json = new JSONObject();
				json.put("client", clientID);
				json.put("type", "ping");
				json.put("ts", ts);

				sendMessage(new JSONArray().put(json).toString().getBytes());
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		if (prefs.getBoolean("UseBattery", false)) {
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

				sendMessage(new JSONArray().put(json).toString().getBytes());
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		if (prefs.getBoolean("UseGPS", false)) {
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

				sendMessage(new JSONArray().put(json).toString().getBytes());
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		if (prefs.getBoolean("UseNetwork", false)) {
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

				sendMessage(new JSONArray().put(json).toString().getBytes());
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		if (prefs.getBoolean("UseWIFI", false)) {
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

				sendMessage(new JSONArray().put(json).toString().getBytes());
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}

		// TODO: cannot poll sensors. register receiver to cache sensor data
		if (prefs.getBoolean("UseSensors", false)) {
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

				sendMessage(new JSONArray().put(json).toString().getBytes());
			} catch (Exception e) {
				Log.e(tag, e.toString());
				e.printStackTrace();
			}
		}
	}

	private void sendMessage (final byte[] msgBuf) {
		boolean encrypt = prefs.getBoolean("SendAES", false);
		boolean compress = prefs.getBoolean("SendGZIP", false);
		String exchangeHost = prefs.getString("ExchangeHost", null);
		int exchangePort = Integer.valueOf(prefs.getString("ExchangePort", "-1"));

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
		}

		assert !encrypt || (skeySpec != null && cipher != null);
		assert exchangeHost != null && exchangePort > 0 && exchangePort < 65536;

		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			CipherOutputStream cos = null;
			GZIPOutputStream zos = null;

			// TODO: send protocol header to signal compression & encryption

			if (encrypt) {
				cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
				final byte[] iv = cipher.getIV();

				// iv.length == cipher block size
				// first byte in stream: (iv.length/16)-1
				// TODO: pointless. AES uses fixed 128bit blocks
				assert iv.length <= 4096 && (iv.length & 0x0f) == 0;
				baos.write((iv.length >> 4)-1);

				// write iv block
				baos.write(iv);

				cos = new CipherOutputStream(baos, cipher);
			}

			if (compress) {
				zos = new GZIPOutputStream((encrypt)?(cos):(baos));
				zos.write(msgBuf);
				zos.finish();
				zos.close();
				if (encrypt) {
					cos.close();
				}
			} else if (encrypt) {
				cos.write(msgBuf);
				cos.close();
			} else {
				baos.write(msgBuf);
			}

			baos.flush();
			final byte[] message = baos.toByteArray();
			baos.close();

			// path MTU is the actual limit here, not only local MTU
			// TODO: make packet fragmentable (clear DF flag)
			if (message.length > 1500) {
				Log.w(tag, "Message probably too long: " + message.length + " bytes");
			}

			DatagramSocket socket = new DatagramSocket();
			// socket.setTrafficClass(0x04 | 0x02); // IPTOS_RELIABILITY | IPTOS_LOWCOST
			socket.send(new DatagramPacket(message, message.length, InetAddress.getByName(exchangeHost), exchangePort));
			socket.close();
			Log.d(tag, "message sent: " + message.length + " bytes (raw: " + msgBuf.length + " bytes)");
		} catch (Exception e) {
			Log.e(tag, e.toString());
			e.printStackTrace();
		}
	}
}
