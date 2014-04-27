package org.openchaos.android.fooping;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ToggleButton;
import android.widget.Toast;
import android.widget.Switch;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;


public class FooPingActivity extends Activity {
	private static final String tag = "FooPingActivity";

	private Context context = this;
	private AlarmManager alarmMgr;
	private PendingIntent alarmIntent;
	private SharedPreferences prefs;

	private boolean alarmRunning;
	private IntervalInfo alarmInterval;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		prefs = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE);

		Intent intent = new Intent(getApplicationContext(), AlarmReceiver.class);
		alarmRunning = (PendingIntent.getBroadcast(getApplicationContext(), 0, intent, PendingIntent.FLAG_NO_CREATE) != null);
		alarmIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, 0);
		alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

		if (alarmRunning) {
			Toast.makeText(context, R.string.local_service_running, Toast.LENGTH_SHORT).show();
		}

		ToggleButton button = (ToggleButton)findViewById(R.id.ButtonStartStop);
		button.setChecked(alarmRunning);
		button.setOnClickListener(StartStopListener);

		Switch switchWidget;
		switchWidget = (Switch)findViewById(R.id.UseBattery);
		switchWidget.setChecked(prefs.getBoolean("UseBattery", true));
		switchWidget.setOnClickListener(ToggleListener);

		switchWidget = (Switch)findViewById(R.id.UseGPS);
		switchWidget.setChecked(prefs.getBoolean("UseGPS", true));
		switchWidget.setOnClickListener(ToggleListener);

		switchWidget = (Switch)findViewById(R.id.UseNetwork);
		switchWidget.setChecked(prefs.getBoolean("UseNetwork", false));
		switchWidget.setOnClickListener(ToggleListener);

		switchWidget = (Switch)findViewById(R.id.UseWIFI);
		switchWidget.setChecked(prefs.getBoolean("UseWIFI", true));
		switchWidget.setOnClickListener(ToggleListener);

		switchWidget = (Switch)findViewById(R.id.UseSensors);
		switchWidget.setChecked(prefs.getBoolean("UseSensors", false));
		switchWidget.setOnClickListener(ToggleListener);

		switchWidget = (Switch)findViewById(R.id.SendAES);
		switchWidget.setChecked(prefs.getBoolean("SendAES", true));
		switchWidget.setOnClickListener(ToggleListener);

		switchWidget = (Switch)findViewById(R.id.SendGZIP);
		switchWidget.setChecked(prefs.getBoolean("SendGZIP", true));
		switchWidget.setOnClickListener(ToggleListener);

		SeekBar seekBarWidget = (SeekBar)findViewById(R.id.updateIntervalSeekBar);
		seekBarWidget.setMax(intervals.length-1);
		seekBarWidget.setProgress(prefs.getInt("updateIntervalID", 6));
		// TODO: problem: new seek bar starts at 0.
		// if updateIntervalID == 0, no progress update is triggered byt setProgress()
		seekBarChangeListener.onProgressChanged(seekBarWidget, prefs.getInt("updateIntervalID", 6), false);
		seekBarWidget.setOnSeekBarChangeListener(seekBarChangeListener);
	}

	private OnClickListener StartStopListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (alarmRunning) {
				Log.d(tag, "onClick(): stop");

				if (alarmMgr != null && alarmIntent != null) {
					alarmMgr.cancel(alarmIntent);
					Toast.makeText(context, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
				}
			} else {
				Log.d(tag, "onClick(): start");

				if (alarmMgr != null && alarmIntent != null) {
					alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, alarmInterval.interval * 1000, alarmIntent);
					Toast.makeText(context, R.string.local_service_started, Toast.LENGTH_SHORT).show();
				}
			}
			alarmRunning = !alarmRunning;
		}
	};

	private OnClickListener ToggleListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			boolean currentState;
			SharedPreferences.Editor editor = prefs.edit();
			switch (v.getId()) {
				case R.id.UseBattery:
					currentState = prefs.getBoolean("UseBattery", true);
					editor.putBoolean("UseBattery", !currentState);
					Log.d(tag, "setUseBattery: " + (!currentState));
					break;
				case R.id.UseGPS:
					currentState = prefs.getBoolean("UseGPS", true);
					editor.putBoolean("UseGPS", !currentState);
					Log.d(tag, "setUseGPS: " + (!currentState));
					break;
				case R.id.UseNetwork:
					currentState = prefs.getBoolean("UseNetwork", false);
					editor.putBoolean("UseNetwork", !currentState);
					Log.d(tag, "setUseNetwork: " + (!currentState));
					break;
				case R.id.UseWIFI:
					currentState = prefs.getBoolean("UseWIFI", true);
					editor.putBoolean("UseWIFI", !currentState);
					Log.d(tag, "setUseWIFI: " + (!currentState));
					break;
				case R.id.UseSensors:
					currentState = prefs.getBoolean("UseSensors", false);
					editor.putBoolean("UseSensors", !currentState);
					Log.d(tag, "setUseSensors: " + (!currentState));
					break;
				case R.id.SendAES:
					currentState = prefs.getBoolean("SendAES", true);
					editor.putBoolean("SendAES", !currentState);
					Log.d(tag, "setSendAES: " + (!currentState));
					break;
				case R.id.SendGZIP:
					currentState = prefs.getBoolean("SendGZIP", true);
					editor.putBoolean("SendGZIP", !currentState);
					Log.d(tag, "setSendGZIP: " + (!currentState));
					break;
				default:
					Log.w(tag, "Unknown switch ID: " + v.getId());
					return;
			}
			((Switch)v).setChecked(!currentState);
			editor.apply();
		}
	};

	private OnSeekBarChangeListener seekBarChangeListener = new OnSeekBarChangeListener() {
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			Log.d(tag, "onProgressChanged(): " + progress);
			if (progress >= 0 && progress < intervals.length) {
				alarmInterval = intervals[progress];
				((TextView)findViewById(R.id.updateInterval)).setText(alarmInterval.name);
				if (fromUser) {
					SharedPreferences.Editor editor = prefs.edit();
					editor.putInt("updateIntervalID", progress);
					editor.apply();
				}
			} else {
				Log.w(tag, "Invalid update interval ID: " + progress);
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
