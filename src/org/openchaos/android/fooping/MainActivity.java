package org.openchaos.android.fooping;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction().add(android.R.id.content, new MainFragment()).commit();
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
}
