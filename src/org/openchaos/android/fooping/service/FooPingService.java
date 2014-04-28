package org.openchaos.android.fooping.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openchaos.android.fooping.R;

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
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.util.Log;


public class FooPingService extends IntentService {
	private static final String tag = "FooPingService";

	private SharedPreferences prefs;
	private LocationManager lm;
	private SensorManager sm;
	private WifiManager wm;

	private SecretKeySpec skeySpec;
	private Cipher cipher;


	public FooPingService() {
		super(tag);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		prefs = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE);
		lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);

		try {
			skeySpec = new SecretKeySpec(prefs.getString("ExchangeKey", null).getBytes("US-ASCII"), "AES");
			cipher = Cipher.getInstance("AES/CFB8/NoPadding");
		} catch (Exception e) {
			Log.e(tag, e.toString());
			e.printStackTrace();
		}
	}

	private final static double truncValue(double value, int scale) {
		return BigDecimal.valueOf(value).setScale(scale, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().doubleValue();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		try {
			// TODO: send each data source in its own packet together with ts and clientID

			JSONObject json = new JSONObject();
			json.put("ts", System.currentTimeMillis());
			json.put("client", prefs.getString("ClientID", "unknown"));

			if (prefs.getBoolean("UseBattery", false)) {
				Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
				if (batteryStatus != null) {
					JSONObject bat_data = new JSONObject();
					int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
					int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
					if (level > 0 && scale > 0) {
						bat_data.put("pct", truncValue(((double)level / (double)scale)*100, 2));
					} else {
						Log.w(tag, "Battery level unknown");
						bat_data.put("pct", -1);
					}
					bat_data.put("health", batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));
					bat_data.put("status", batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
					bat_data.put("plug", batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
					bat_data.put("volt", batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
					bat_data.put("temp", batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
//					bat_data.put("tech", batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
//					bat_data.put("present", batteryStatus.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false));
					json.put("battery", bat_data);
				}
			}

			if (prefs.getBoolean("UseWIFI", false)) {
				List<ScanResult> wifiScan = wm.getScanResults();
				JSONArray wifi_list = new JSONArray();
				for (ScanResult wifi : wifiScan) {
					JSONObject wifi_data = new JSONObject();
					wifi_data.put("BSSID", wifi.BSSID);
					wifi_data.put("SSID", wifi.SSID);
					wifi_data.put("freq", wifi.frequency);
					wifi_data.put("level", wifi.level);
//					wifi_data.put("cap", wifi.capabilities);
//					wifi_data.put("ts", wifi.timestamp);
					wifi_list.put(wifi_data);
				}
				json.put("wifi", wifi_list);
			}

			if (prefs.getBoolean("UseGPS", false)) {
				Location last_GPS = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				JSONObject loc_data = new JSONObject();
				loc_data.put("ts", last_GPS.getTime());
				loc_data.put("lat", last_GPS.getLatitude());
				loc_data.put("lon",  last_GPS.getLongitude());
				if (last_GPS.hasAltitude()) loc_data.put("alt", truncValue(last_GPS.getAltitude(), 4));
				if (last_GPS.hasAccuracy()) loc_data.put("acc", truncValue(last_GPS.getAccuracy(), 4));
				if (last_GPS.hasSpeed()) loc_data.put("speed", truncValue(last_GPS.getSpeed(), 4));
				if (last_GPS.hasBearing()) loc_data.put("bearing", truncValue(last_GPS.getBearing(), 4));
				json.put("loc_gps", loc_data);
			}

			if (prefs.getBoolean("UseNetwork", false)) {
				Location last_NETWORK = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				JSONObject loc_data = new JSONObject();
				loc_data.put("ts", last_NETWORK.getTime());
				loc_data.put("lat", last_NETWORK.getLatitude());
				loc_data.put("lon",  last_NETWORK.getLongitude());
				if (last_NETWORK.hasAltitude()) loc_data.put("alt", truncValue(last_NETWORK.getAltitude(), 4));
				if (last_NETWORK.hasAccuracy()) loc_data.put("acc", truncValue(last_NETWORK.getAccuracy(), 4));
				if (last_NETWORK.hasSpeed()) loc_data.put("speed", truncValue(last_NETWORK.getSpeed(), 4));
				if (last_NETWORK.hasBearing()) loc_data.put("bearing", truncValue(last_NETWORK.getBearing(), 4));
				json.put("loc_net", loc_data);
			}

			// TODO: cannot poll sensors. register receiver to cache sensor data
			if (prefs.getBoolean("UseSensors", false)) {
				List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
				JSONArray sensor_list = new JSONArray();
				for (Sensor sensor : sensors) {
					JSONObject sensor_info = new JSONObject();
					sensor_info.put("name", sensor.getName());
					sensor_info.put("type", sensor.getType());
					sensor_info.put("vendor", sensor.getVendor());
					sensor_info.put("version", sensor.getVersion());
					sensor_info.put("power", truncValue(sensor.getPower(), 2));
//					sensor_info.put("resolution", sensor.getResolution());
//					sensor_info.put("range", sensor.getMaximumRange());
					sensor_list.put(sensor_info);
				}
				json.put("sensors", sensor_list);
			}

			new _sendUDP().execute(new JSONArray().put(json).toString().getBytes());
		} catch (Exception e) {
			Log.e(tag, e.toString());
			e.printStackTrace();
		}
	}

	private class _sendUDP extends AsyncTask <byte[], Void, Void> {
		@Override
		protected Void doInBackground(final byte[]... logBuf) {
			boolean encrypt = prefs.getBoolean("SendAES", false);
			boolean compress = prefs.getBoolean("SendGZIP", false);
			String exchangeHost = prefs.getString("ExchangeHost", null);
			int exchangePort = prefs.getInt("ExchangePort", -1);

			assert !encrypt || (cipher != null && skeySpec != null);
			assert exchangeHost != null && exchangePort > 0;

			final int count = logBuf.length;
			for (int i = 0; i < count; i++) {
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
						assert (iv.length & 0x0f) == 0;
						baos.write((iv.length >> 4)-1);
						// write iv block
						baos.write(iv);

						cos = new CipherOutputStream(baos, cipher);
					}

					if (compress) {
						zos = new GZIPOutputStream((encrypt)?(cos):(baos));
						zos.write(logBuf[i]);
						zos.finish();
						zos.close();
						if (encrypt) {
							cos.close();
						}
					} else if (encrypt) {
						cos.write(logBuf[i]);
						cos.close();
					} else {
						baos.write(logBuf[i]);
					}

					baos.flush();
					final byte[] message = baos.toByteArray();
					baos.close();

					// path MTU is the actual limit here, not only local MTU
					// TODO: make packet fragmentable (clear DF flag) or handle ICMP errors
					if (message.length > 1500) {
						Log.w(tag, "Message probably too long: " + message.length + " bytes");
					}

					DatagramPacket packet = new DatagramPacket(message, message.length,
							InetAddress.getByName(exchangeHost), exchangePort);
					DatagramSocket socket = new DatagramSocket();
					socket.send(packet);
					socket.close();
					Log.d(tag, "message sent: " + message.length + " bytes (raw: " + logBuf[i].length + " bytes)");
				} catch (Exception e) {
					Log.e(tag, e.toString());
					e.printStackTrace();
				}
			}

			return null;
		}
	}
}
