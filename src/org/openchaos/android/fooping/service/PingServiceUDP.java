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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;


public class PingServiceUDP extends WakefulBroadcastReceiver {
	private static final String tag = PingServiceUDP.class.getSimpleName();

	@Override
	public void onReceive(final Context context, Intent intent) {
		Log.d(tag, "Broadcast received. Starting service");
		startWakefulService(context, new Intent(PingService.ACTION_DEFAULT, null, context, PingService.class).putExtra(PingService.EXTRA_RECEIVER, new ResultReceiver(null) {
			@Override
			 protected void onReceiveResult(int resultCode, Bundle resultData) {
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

				String exchangeHost = prefs.getString("ExchangeHost", null);
				int exchangePort = Integer.valueOf(prefs.getString("ExchangePort", "-1"));

				if (exchangeHost == null || exchangePort <= 0 || exchangePort >= 65536) {
					Log.e(tag, "Invalid server name or port");
					return;
				}

				ArrayList<Bundle> results = resultData.getParcelableArrayList(PingService.EXTRA_RESULTS);
				if (results == null) {
					Log.e(tag, "NULL results received");
					return;
				}

				for (Bundle result : results) {
					if (result == null) {
						Log.e(tag, "NULL result received");
						continue;
					}

					byte[] output = result.getByteArray(PingService.EXTRA_OUTPUT);
					long msglen = result.getLong(PingService.EXTRA_MSGLEN);

					if (output == null) {
						Log.e(tag, "NULL output received");
						continue;
					}

					// path MTU is the actual limit here, not only local MTU
					// TODO: make packet fragmentable (clear DF flag)
					if (output.length > 1500) {
						Log.w(tag, "Message probably too long: " + output.length + " bytes");
					}

					// send result via UDP
					try {
						DatagramSocket socket = new DatagramSocket();
						// socket.setTrafficClass(0x04 | 0x02); // IPTOS_RELIABILITY | IPTOS_LOWCOST
						socket.send(new DatagramPacket(output, output.length, InetAddress.getByName(exchangeHost), exchangePort));
						socket.close();
						Log.d(tag, "Message sent: " + output.length + " bytes (raw: " + msglen + " bytes)");
					} catch (Exception e) {
						Log.e(tag, "UDP send failed", e);
					}
				}

				Intent serviceIntent = resultData.getParcelable(PingService.EXTRA_INTENT);
				if (serviceIntent == null || !WakefulBroadcastReceiver.completeWakefulIntent(serviceIntent)) {
					Log.w(tag, "Wake lock release failed. No active wake lock?");
				}
			}
 		}));
	}
}
