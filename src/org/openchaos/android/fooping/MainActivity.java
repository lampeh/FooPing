package org.openchaos.android.fooping;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// set default preferences here
		// crash with ClassCastException on incompatible preferences
		// TODO: catch Exception, clear and overwrite old preferences
		// TODO: use PreferenceFragment default magic
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = prefs.edit();
/*
		editor.putBoolean("UseBattery", prefs.getBoolean("UseBattery", true));
		editor.putBoolean("UseWIFI", prefs.getBoolean("UseWIFI", true));
		editor.putBoolean("UseGPS", prefs.getBoolean("UseGPS", true));
		editor.putBoolean("UseNetwork", prefs.getBoolean("UseNetwork", false));
		editor.putBoolean("UseSensors", prefs.getBoolean("UseSensors", false));
		editor.putBoolean("SendGZIP", prefs.getBoolean("SendGZIP", true));
		editor.putBoolean("SendAES", prefs.getBoolean("SendAES", true));
*/
		editor.putInt("UpdateIntervalID", prefs.getInt("UpdateIntervalID", 6));
		editor.putLong("UpdateInterval", prefs.getLong("UpdateInterval", 3600));
		editor.putString("ClientID", prefs.getString("ClientID", "client1"));
		editor.putString("ExchangeHost", prefs.getString("ExchangeHost", "85.10.240.255"));
		editor.putInt("ExchangePort", prefs.getInt("ExchangePort", 23042));
		editor.putString("ExchangeKey", prefs.getString("ExchangeKey", "m!ToSC]vb=:<b&XL.|Yq#LYE{V+$Mc~y"));
		editor.apply();

		setContentView(R.layout.main_activity);

		if (savedInstanceState == null && findViewById(R.id.fragment_container) != null) {
			getFragmentManager().beginTransaction().add(R.id.fragment_container, new MainFragment()).commit();
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
					.replace(R.id.fragment_container, new SettingsFragment())
					.addToBackStack(null).commit();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
