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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class PendingIntentReceiver extends BroadcastReceiver {
	private static final String tag = "PendingIntentReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!(intent.getAction() == Intent.ACTION_RUN && intent.hasExtra(Intent.EXTRA_INTENT))) {
			return;
		}

		Log.d(tag, "Broadcast received. Executing pending intent");
		try {
			// TODO: think again. is this safe?
			((PendingIntent)intent.getParcelableExtra(Intent.EXTRA_INTENT)).send();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
