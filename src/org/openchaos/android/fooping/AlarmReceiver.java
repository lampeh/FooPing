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

import org.openchaos.android.fooping.service.PingService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


// TODO: this could be more generic with externally supplied class
// TODO: pass command from received intent to service
public class AlarmReceiver extends BroadcastReceiver {
	private static final String tag = "AlarmReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO: verify received intent?
		Log.d(tag, "Alarm received");
		context.startService(new Intent(context, PingService.class));
	}
}
