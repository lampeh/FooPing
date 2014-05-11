/*
 * Copyright 2014 Hauke Lampe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */


package org.openchaos.android.fooping;

import org.openchaos.android.fooping.service.PingService.PingServiceReceiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;


public class BootReceiver extends BroadcastReceiver {
	private static final String tag = "BootReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(tag, "BootReceiver.onReceive()");
		Context appContext = context.getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
		if (prefs.getBoolean("StartOnBoot", false)) {
			long updateInterval = Long.valueOf(prefs.getString("UpdateInterval", "-1"));
			if (updateInterval > 0) {
				Log.d(tag, "Starting alarm");
				((AlarmManager)context.getSystemService(Context.ALARM_SERVICE))
					.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, updateInterval * 1000, updateInterval * 1000,
						PendingIntent.getBroadcast(appContext, 0, new Intent(appContext, PingServiceReceiver.class), PendingIntent.FLAG_CANCEL_CURRENT));
			}
		}
	}
}
