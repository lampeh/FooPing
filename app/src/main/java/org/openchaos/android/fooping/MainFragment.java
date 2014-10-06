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


package org.openchaos.android.fooping;

import org.openchaos.android.fooping.service.PingServiceUDP;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;


public class MainFragment extends Fragment {
	private static final String tag = MainFragment.class.getSimpleName();

	private Activity activity;
	private Context appContext;
	private SharedPreferences prefs;
	private AlarmManager alarmManager;
	private boolean alarmRunning;

	private Intent serviceIntent;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.main_fragment, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		Log.d(tag, "onActivityCreated()");
		super.onActivityCreated(savedInstanceState);

		activity = getActivity();
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		alarmManager = (AlarmManager)activity.getSystemService(Context.ALARM_SERVICE);

		// alarm intent might live longer than this activity
		appContext = activity.getApplicationContext();
		serviceIntent = new Intent(appContext, PingServiceUDP.class);

		if (prefs.getBoolean("EnableGCM", false)) {
			initGCM();
		}

		// NB: a pending intent does not reliably indicate a running alarm
		// always cancel the intent after stopping the alarm
		alarmRunning = (PendingIntent.getBroadcast(appContext, 0, serviceIntent, PendingIntent.FLAG_NO_CREATE) != null);

		if (alarmRunning) {
			Log.d(tag, "Found pending alarm intent");
			Toast.makeText(activity, R.string.alarm_running, Toast.LENGTH_SHORT).show();
		}

		ToggleButton button = (ToggleButton)activity.findViewById(R.id.StartStopButton);
		button.setChecked(alarmRunning);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				alarmRunning = !alarmRunning;
				if (alarmRunning) {
					Log.d(tag, "onClick(): start");
					long updateInterval = Long.valueOf(prefs.getString("UpdateInterval", "-1"));
					if (updateInterval > 0) {
						alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, updateInterval * 1000,
								PendingIntent.getBroadcast(appContext, 0, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT));
						Toast.makeText(activity, R.string.alarm_started, Toast.LENGTH_SHORT).show();
					}
				} else {
					Log.d(tag, "onClick(): stop");
					PendingIntent alarmIntent = PendingIntent.getBroadcast(appContext, 0, serviceIntent, PendingIntent.FLAG_NO_CREATE);
					if (alarmIntent != null) {
						alarmManager.cancel(alarmIntent);
						alarmIntent.cancel();
						Toast.makeText(activity, R.string.alarm_stopped, Toast.LENGTH_SHORT).show();
					}
				}
			}
		});
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If
	 * it doesn't, display a dialog that allows users to download the APK from
	 * the Google Play Store or enable it in the device's system settings.
	 */
	private boolean initGCM() {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(appContext);

		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, activity, 0).show();
			} else {
				Log.w(tag, "This device is not supported by Google Play Services");
			}
			return false;
		}

		String regid = prefs.getString("GCM_ID", "");
		if (regid.isEmpty()) {
			final String gcm_sender = prefs.getString("GCM_SENDER", "");
			if ("".equals(gcm_sender)) {
				Log.w(tag, "No GCM Sender ID configured. Cannot register");
				return false;
			}

			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(appContext);
					String regid = "";

					try {
						regid = gcm.register(gcm_sender);
					} catch (Exception e) {
						Log.e(tag, "Failed to register GCM client", e);
					}

					if (!regid.isEmpty()) {
						prefs.edit().putString("GCM_ID", regid).commit();
					}

					return null;
				}
			}.execute();
		}

		return true;
	}
}
