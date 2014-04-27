package org.openchaos.android.fooping;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.openchaos.android.fooping.service.FooPingService;


public class AlarmReceiver extends BroadcastReceiver {
	private static final String tag = "FooPingAlarmReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(tag, "alarm received");
		// TODO: verify received intent?
		Intent serviceIntent = new Intent(context, FooPingService.class);
		context.startService(serviceIntent);
	}
}
