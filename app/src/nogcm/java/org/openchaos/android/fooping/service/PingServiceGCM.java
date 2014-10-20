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


package org.openchaos.android.fooping.service;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class PingServiceGCM extends BroadcastReceiver {
	private static final String tag = PingServiceGCM.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(tag, "Received broadcast: " + intent.toString());
		Log.w(tag, "GCM control is disabled. Message ignored");
	}

	public static boolean initGCM(final Activity activity) {
		return false;
	}
}
