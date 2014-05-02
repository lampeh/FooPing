package org.openchaos.android.fooping;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ToggleButton;


public class MainFragment extends Fragment {
	private static final String tag = "MainFragment";

	private Activity activity;
	private Context appContext;
	private SharedPreferences prefs;
	private AlarmManager alarmManager;
	private boolean alarmRunning;

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
		alarmRunning = (PendingIntent.getBroadcast(appContext, 0, new Intent(appContext, AlarmReceiver.class), PendingIntent.FLAG_NO_CREATE)
				!= null);

		if (alarmRunning) {
			Log.d(tag, "Found pending alarm intent");
			Toast.makeText(activity, R.string.service_running, Toast.LENGTH_SHORT).show();
		}

		ToggleButton button = (ToggleButton)activity.findViewById(R.id.StartStopButton);
		button.setChecked(alarmRunning);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				PendingIntent alarmIntent = PendingIntent.getBroadcast(appContext, 0, new Intent(appContext, AlarmReceiver.class), 0);
				alarmRunning = !alarmRunning;
				if (alarmRunning) {
					Log.d(tag, "onClick(): start");
					long updateInterval = Long.valueOf(prefs.getString("UpdateInterval", "-1"));
					if (updateInterval > 0) {
						alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, updateInterval * 1000, alarmIntent); 
						Toast.makeText(activity, R.string.service_started, Toast.LENGTH_SHORT).show();
					} else {
						Log.e(tag, "Invalid UpdateInterval in preferences");
						// didn't start alarm. clean up pending intent
						alarmIntent.cancel();
					}
				} else {
					Log.d(tag, "onClick(): stop");
					alarmManager.cancel(alarmIntent);
					alarmIntent.cancel();
					Toast.makeText(activity, R.string.service_stopped, Toast.LENGTH_SHORT).show();
				}
			}
		});
	}
}
