package org.openchaos.android.fooping;

import org.openchaos.android.fooping.service.PingService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class AlarmReceiver extends BroadcastReceiver {
	private static final String tag = "AlarmReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO: verify received intent?
		Log.d(tag, "alarm received");
		context.startService(new Intent(context, PingService.class));
	}
}
