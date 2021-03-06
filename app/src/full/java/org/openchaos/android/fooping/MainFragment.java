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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;


public class MainFragment extends BaseMainFragment {
	private static final String tag = MainFragment.class.getSimpleName();

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		Activity activity = getActivity();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		if (prefs.getBoolean("EnableGCM", false)) {
			if (!initGCM(prefs, activity)) {
				Log.w(tag, "initGCM() failed");
			}
		}
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If
	 * it doesn't, display a dialog that allows users to download the APK from
	 * the Google Play Store or enable it in the device's system settings.
	 */
	private boolean initGCM(final SharedPreferences prefs, Activity activity) {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);

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
			final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(activity);
			final String gcm_sender = prefs.getString("GCM_SENDER", "");

			if (gcm_sender.isEmpty()) {
				Log.w(tag, "No GCM Sender ID configured. Cannot register");
				return false;
			}

			new AsyncTask<Void, Void, Void>() {
				@SuppressLint("CommitPrefEdits")
				@Override
				protected Void doInBackground(Void... params) {
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
