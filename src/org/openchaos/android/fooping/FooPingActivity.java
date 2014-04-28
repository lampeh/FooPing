package org.openchaos.android.fooping;

import java.util.List;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ToggleButton;
import android.widget.Toast;
import android.widget.Switch;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.androidwtf.android.Views;


public class FooPingActivity extends Activity {
	private static final String tag = "FooPingActivity";

	private Context context = this;
	private AlarmManager alarmManager;
	private PendingIntent alarmIntent;
	private SharedPreferences prefs;
	private Resources res;

	private boolean alarmRunning;
	private IntervalInfo alarmInterval;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		prefs = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE);
		res = getResources();

		// set default preferences here
		// crash with ClassCastException on incompatible preferences
		// TODO: catch Exception, clear preferences and try again
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean("UseBattery", prefs.getBoolean("UseBattery", true));
		editor.putBoolean("UseWIFI", prefs.getBoolean("UseWIFI", true));
		editor.putBoolean("UseGPS", prefs.getBoolean("UseGPS", true));
		editor.putBoolean("UseNetwork", prefs.getBoolean("UseNetwork", false));
		editor.putBoolean("UseSensors", prefs.getBoolean("UseSensors", false));
		editor.putBoolean("SendGZIP", prefs.getBoolean("SendGZIP", true));
		editor.putBoolean("SendAES", prefs.getBoolean("SendAES", true));
		editor.putInt("updateIntervalID", prefs.getInt("updateIntervalID", 6));
		editor.apply();

		// alarm intent might live longer than this activity
		Context app = getApplicationContext();
		Intent intent = new Intent(app, AlarmReceiver.class);
		alarmRunning = (PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_NO_CREATE) != null);
		alarmIntent = PendingIntent.getBroadcast(app, 0, intent, 0);
		alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);

		if (alarmRunning) {
			Log.d(tag, "Found pending alarm intent");
			Toast.makeText(context, R.string.local_service_running, Toast.LENGTH_SHORT).show();
		}

		List<Switch> switches = Views.find((ViewGroup)findViewById(R.id.RootView), Switch.class);
		for (Switch switchWidget : switches) {
			String resName = res.getResourceEntryName(switchWidget.getId());
			Log.d(tag, "Initalizing switch: " + resName);

			if (prefs.contains(resName)) {
				try {
					switchWidget.setChecked(prefs.getBoolean(resName, false));
					switchWidget.setOnClickListener(SwitchListener);
				} catch (Exception e) {
					Log.e(tag, "Failed to initialize switch: " + resName);
					e.printStackTrace();
				}
			} else {
				Log.w(tag, "No preference with name " + resName);
			}
		}

		int updateIntervalID = prefs.getInt("updateIntervalID", -1);
		if (updateIntervalID < 0 || updateIntervalID >= intervals.length) {
			Log.w(tag, "Invalid updateIntervalID in preferences");
			updateIntervalID = intervals.length-1;
		}

		SeekBar seekBarWidget = (SeekBar)findViewById(R.id.updateIntervalSeekBar);
		seekBarWidget.setMax(intervals.length-1);
		seekBarWidget.setProgress(updateIntervalID);
		IntervalChangeListener.onProgressChanged(seekBarWidget, updateIntervalID, false);
		seekBarWidget.setOnSeekBarChangeListener(IntervalChangeListener);

		ToggleButton button = (ToggleButton)findViewById(R.id.ButtonStartStop);
		button.setChecked(alarmRunning);
		button.setOnClickListener(StartStopListener);
	}

	private OnClickListener StartStopListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (alarmRunning) {
				Log.d(tag, "onClick(): stop");
				alarmManager.cancel(alarmIntent);
				Toast.makeText(context, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
			} else {
				Log.d(tag, "onClick(): start");
				alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, alarmInterval.interval * 1000, alarmIntent);
				Toast.makeText(context, R.string.local_service_started, Toast.LENGTH_SHORT).show();
			}
			alarmRunning = !alarmRunning;
		}
	};

	private OnClickListener SwitchListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			String resName = res.getResourceEntryName(v.getId());
			Log.d(tag, "SwitchListener for element: " + resName);

			if (prefs.contains(resName)) {
				try {
					boolean currentState = prefs.getBoolean(resName, false);
					prefs.edit().putBoolean(resName, !currentState).apply();
					Log.d(tag, "Set " + resName + ": " + (!currentState));
				} catch (Exception e) {
					Log.e(tag, "Failed to set " + resName);
					e.printStackTrace();
				}
			} else {
				Log.w(tag, "No preference with name " + resName);
			}
		}
	};

	private OnSeekBarChangeListener IntervalChangeListener = new OnSeekBarChangeListener() {
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int intervalID, boolean fromUser) {
			if (intervalID >= 0 && intervalID < intervals.length) {
				alarmInterval = intervals[intervalID];
				((TextView)findViewById(R.id.updateInterval)).setText(alarmInterval.name);
				if (fromUser) {
					prefs.edit().putInt("updateIntervalID", intervalID).apply();
				}
				Log.d(tag, "Set updateIntervalID: " + intervalID);
			} else {
				Log.w(tag, "Invalid update interval ID: " + intervalID);
			}
		}
	};

	private static final class IntervalInfo {
		public final long interval;
		public final String name;

		private IntervalInfo(final long interval, final String name) {
			this.interval = interval;
			this.name = name;
		}
	}

	private final IntervalInfo[] intervals = new IntervalInfo[] {
			new IntervalInfo(1, "1s"),
			new IntervalInfo(10, "10s"),
			new IntervalInfo(60, "1 minute"),
			new IntervalInfo(300, "5 minutes"),
			new IntervalInfo(900, "15 minutes"),
			new IntervalInfo(1800, "30 minutes"),
			new IntervalInfo(3600, "1 hour"),
			new IntervalInfo(86400, "1 day"),
/*
 			new IntervalInfo(1, getString(R.string.interval_1s)),
			new IntervalInfo(10, getString(R.string.interval_10s)),
			new IntervalInfo(60, getString(R.string.interval_60s)),
			new IntervalInfo(300, getString(R.string.interval_300s)),
			new IntervalInfo(900, getString(R.string.interval_900s)),
			new IntervalInfo(1800, getString(R.string.interval_1800s)),
			new IntervalInfo(3600, getString(R.string.interval_3600s)),
			new IntervalInfo(86400, getString(R.string.interval_86400s)),
*/
	};
}
