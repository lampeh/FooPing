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

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public class PingServiceGCM extends WakefulBroadcastReceiver {
	private static final String tag = PingServiceGCM.class.getSimpleName();

	@Override
	public void onReceive(final Context context, Intent intent) {
		Log.d(tag, "Broadcast received");

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (!prefs.getBoolean("EnableGCM", false)) {
			Log.w(tag, "GCM control is disabled. Message ignored");
			return;
		}

		final String gcm_sender = prefs.getString("GCM_SENDER", "");
		if (gcm_sender == "") {
			Log.w(tag, "No GCM_SENDER ID configured. Message ignored");
			return;
		}

		Bundle extras = intent.getExtras();
		if (extras.isEmpty()) {
			Log.w(tag, "Extra section is empty. Message ignored");
			return;
		}

		final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
		String messageType = gcm.getMessageType(intent);

		/*
		 * Filter messages based on message type. Since it is likely that GCM
		 * will be extended in the future with new message types, just ignore
		 * any message types you're not interested in, or that you don't
		 * recognize.
		 */
		if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
			Log.w(tag, "Send error: " + extras.toString());
		} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
			Log.d(tag, "Messages deleted: " + extras.toString());
		// If it's a regular GCM message, do some work.
		} else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
			Log.d(tag, "Received command: " + extras.toString());

			final String action = extras.getString("action");
			final String msgId = extras.getString("message_id");
			if (action == null || msgId == null) {
				Log.w(tag, "Required request parameters not set. Message ignored");
				return;
			}

			startWakefulService(context, new Intent(action, null, context, PingService.class).putExtra(PingService.EXTRA_RECEIVER, new ResultReceiver(null) {
				@Override
				 protected void onReceiveResult(int resultCode, Bundle resultData) {
					String output_string = "";
					long msglen = 0;

					Bundle data = new Bundle();
					data.putString("action", action);
					data.putString("message_id", "result-" + msgId);
					data.putString("result_code", Integer.toString(resultCode));

					ArrayList<Bundle> results = resultData.getParcelableArrayList(PingService.EXTRA_RESULTS);
					if (results != null) {
						Log.d(tag, "Results received: " + results.size());

						JSONArray outputs = new JSONArray();

						for (Bundle result : results) {
							byte[] output = result.getByteArray(PingService.EXTRA_OUTPUT);
							msglen += result.getLong(PingService.EXTRA_MSGLEN);

							if (output == null) {
								Log.e(tag, "NULL output received");
								continue;
							}

							outputs.put(Base64.encodeToString(output, Base64.NO_WRAP));
						}

						output_string = outputs.toString();
						data.putString("output", output_string);
					} else {
						Log.e(tag, "NULL results received");
					}

					try {
						gcm.send(gcm_sender + "@gcm.googleapis.com", "result-" + msgId, data);
						Log.d(tag, "message sent: " + output_string.getBytes().length + " bytes (raw: " + msglen + " bytes)");
					} catch (IOException e) {
						Log.e(tag, e.toString());
						e.printStackTrace();
					}

					Intent serviceIntent = resultData.getParcelable(PingService.EXTRA_INTENT);
					if (serviceIntent != null) {
						if (!WakefulBroadcastReceiver.completeWakefulIntent(serviceIntent)) {
							Log.w(tag, "completeWakefulIntent() failed. no active wake lock?");
						}
					}
				}
	 		}));
		}
	}
}
