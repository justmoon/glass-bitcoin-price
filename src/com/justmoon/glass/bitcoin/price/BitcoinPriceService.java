/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.justmoon.glass.bitcoin.price;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.TimelineManager;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * Service owning the LiveCard living in the timeline.
 */
public class BitcoinPriceService extends Service {

    private static final String TAG = "BitcoinPriceService";
    private static final String LIVE_CARD_ID = "bitcoinprice";

    private TimelineManager mTimelineManager;
    private LiveCard mLiveCard;
    private RemoteViews mViews;

    @Override
    public void onCreate() {
        super.onCreate();
        mTimelineManager = TimelineManager.from(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class RetrievePriceTask extends AsyncTask<Void, Void, Long> {

        private static final String TAG = "RetrievePriceTask";

    	@Override
    	protected Long doInBackground(Void... params) {
    		// TODO Auto-generated method stub
    		StringBuilder builder = new StringBuilder();
    		HttpClient client = new DefaultHttpClient();
    		HttpGet httpGet = new HttpGet("https://www.bitstamp.net/api/ticker/");
    		
    		try {
    			HttpResponse response = client.execute(httpGet);
    			StatusLine statusLine = response.getStatusLine();
    			int statusCode = statusLine.getStatusCode();
    			if (statusCode == 200) {
    				HttpEntity entity = response.getEntity();
    				InputStream content = entity.getContent();
    				BufferedReader reader = new BufferedReader(
    						new InputStreamReader(content));
    				String line;
    				while ((line = reader.readLine()) != null) {
    					builder.append(line);
    				}
    				Log.v(TAG, "Your data: " + builder.toString()); //response data
    				JSONObject json = new JSONObject(new JSONTokener(builder.toString()));
    				Log.d(TAG, "Got price: " + json.getLong("last"));
    				return json.getLong("last");
    			} else {
    				Log.e(TAG, "Failed to download file");
    			}
    		} catch (ClientProtocolException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		} catch (JSONException e) {
    			e.printStackTrace();
    		}
    		
    		return null;
    	}
    	
    	@Override
        protected void onPostExecute(Long result) {
            Calendar c = Calendar.getInstance(); 

            mViews.setTextViewText(R.id.currency, "BITCOIN");
            mViews.setTextViewText(R.id.price, "" + result);
            mViews.setTextViewText(R.id.source, "Bitstamp "+c.get(Calendar.HOUR)+":"+c.get(Calendar.MINUTE)+
            		" "+((c.get(Calendar.AM_PM) == Calendar.AM) ? "AM" : "PM"));
            mLiveCard.setViews(mViews);
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mLiveCard == null) {
            Log.d(TAG, "Publishing LiveCard");
            mLiveCard = mTimelineManager.getLiveCard(LIVE_CARD_ID);

            // Keep track of the callback to remove it before unpublishing.
            //mCallback = new PriceDrawer(this);
            //mLiveCard.enableDirectRendering(true).getSurfaceHolder().addCallback(mCallback);
            mViews = new RemoteViews(getPackageName(), R.layout.card_price);

            RetrievePriceTask get = new RetrievePriceTask();
            get.execute();

            mViews.setTextViewText(R.id.price, "???");
            mLiveCard.setViews(mViews);
            mLiveCard.setNonSilent(true);

            Intent menuIntent = new Intent(this, MenuActivity.class);
            mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));

            mLiveCard.publish();
            Log.d(TAG, "Done publishing LiveCard");
        } else {
            // TODO(alainv): Jump to the LiveCard when API is available.
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mLiveCard != null && mLiveCard.isPublished()) {
            Log.d(TAG, "Unpublishing LiveCard");
            mLiveCard.unpublish();
            mLiveCard = null;
        }
        super.onDestroy();
    }
}
