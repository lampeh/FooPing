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

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.UUID;


public class PingServiceGCM extends WakefulBroadcastReceiver {
	private static final String tag = PingServiceGCM.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(tag, "Received broadcast: " + intent.toString());

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (!prefs.getBoolean("EnableGCM", false)) {
			Log.w(tag, "GCM control is disabled. Message ignored");
			return;
		}

		final String gcm_sender = prefs.getString("GCM_SENDER", "");
		if ("".equals(gcm_sender)) {
			Log.w(tag, "No GCM Sender ID configured. Message ignored");
			return;
		}

		Bundle extras = intent.getExtras();
		if ((extras == null) || extras.isEmpty()) {
			Log.w(tag, "Extra section is empty. Message ignored");
			return;
		}

		final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
		String messageType = gcm.getMessageType(intent);
		if (messageType == null) {
			Log.w(tag, "No GCM message found. Message ignored");
			return;
		}

		/*
		 * Filter messages based on message type. Since it is likely that GCM
		 * will be extended in the future with new message types, just ignore
		 * any message types you're not interested in, or that you don't
		 * recognize.
		 */
		if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
			Log.w(tag, "Send error: " + extras.toString());
		} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
			Log.i(tag, "Messages deleted: " + extras.toString());
			// If it's a regular GCM message, do some work.
		} else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
			Log.i(tag, "Received command: " + extras.toString());

			final String action = extras.getString("action");
			final String msgId = extras.getString("message_id");
			if ((action == null) || (msgId == null)) {
				Log.w(tag, "Required request parameters not set. Message ignored");
				return;
			}

			startWakefulService(context, new Intent(action, null, context, PingService.class).putExtra(PingService.EXTRA_RECEIVER, new ResultReceiver(null) {
				@Override
				protected void onReceiveResult(int resultCode, Bundle resultData) {
					String outputString = "";
					long rawLength = 0;

					Bundle data = new Bundle();
					data.putString("action", action);
					data.putString("message_id", msgId);
					data.putString("result_code", Integer.toString(resultCode));

					ArrayList<Bundle> results = resultData.getParcelableArrayList(PingService.EXTRA_RESULTS);
					if (results != null) {
						Log.d(tag, "Results received: " + results.size());

						JSONArray outputs = new JSONArray();

						for (Bundle result : results) {
							if (result == null) {
								Log.e(tag, "NULL result received");
								continue;
							}

							byte[] output = result.getByteArray(PingService.EXTRA_OUTPUT);
							rawLength += result.getLong(PingService.EXTRA_MSGLEN);

							if (output == null) {
								Log.e(tag, "NULL output received");
								continue;
							}

							outputs.put(Base64.encodeToString(output, Base64.NO_WRAP));
						}

						outputString = outputs.toString();
						data.putString("output", outputString);
					} else {
						Log.e(tag, "NULL results received");
					}

					long outputLength = outputString.getBytes().length;
					if (outputLength > 4096) {
						Log.w(tag, "Message probably too long: " + outputLength + " bytes");
					}

					try {
						gcm.send(gcm_sender + "@gcm.googleapis.com", UUID.randomUUID().toString(), data);
						Log.d(tag, "Message sent: " + outputLength + " bytes (raw: " + rawLength + " bytes)");
					} catch (Exception e) {
						Log.e(tag, "GCM send failed", e);
					}

					Intent serviceIntent = resultData.getParcelable(PingService.EXTRA_INTENT);
					if ((serviceIntent == null) || !WakefulBroadcastReceiver.completeWakefulIntent(serviceIntent)) {
						Log.w(tag, "Wake lock release failed. No active wake lock?");
					}
				}
			}));
		} else {
			Log.d(tag, "Unknown GCM message type. Message ignored: " + extras.toString());
		}
	}
}
