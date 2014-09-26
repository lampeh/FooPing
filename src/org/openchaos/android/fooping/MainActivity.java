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

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;


public class MainActivity extends Activity {
	private static final String tag = "MainActivity";

	private SharedPreferences prefs;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction().add(android.R.id.content, new MainFragment()).commit();
		}

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean("EnableGCM", false)) {
			initGCM();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean("EnableGCM", false)) {
			initGCM();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.Settings:
				getFragmentManager().beginTransaction()
					.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
					.replace(android.R.id.content, new SettingsFragment())
					.addToBackStack(null).commit();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If
	 * it doesn't, display a dialog that allows users to download the APK from
	 * the Google Play Store or enable it in the device's system settings.
	 */
	private boolean initGCM() {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0).show();
			} else {
				Log.i(tag, "This device is not supported by Google Play Services");
			}
			return false;
		}

		String regid = prefs.getString("GCM_ID", "");
		if (regid.isEmpty()) {
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
					String regid = "";

					try {
						regid = gcm.register(getString(R.string.GCMSenderID));
					} catch (Exception e) {
						Log.e(tag, e.toString());
						e.printStackTrace();
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
