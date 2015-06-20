package org.sonarlog.ioio;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOService;

import org.sonarlog.R;
import org.sonarlog.Project;
import org.sonarlog.SonarLoggerActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public class SonarReader extends IOIOService {
	private static final String TAG = "SonarReader";

	public static String projectName = "default";
	public Project prj;
	private NotificationManager mNM;
	private NMEAParser parser;
	public static CopyOnWriteArrayList<GpsReading> positions;
	public static Queue<SonarReading> depths;

	private LocationManager lm;
	private MyLocationListener locationListener;

	private static boolean showingDebugToast = false;
	private int lastStatus = 0;
	
	public static double MIN_ACCURACY_METERS = 5;
	int MIN_TIME_MILLIS = 100;
	int MIN_DISTANCE_METERS = 0;
	double MAX_INTERPOLATION_DIST = 20;
	
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new BaseIOIOLooper() {
			
			//output signals
			private DigitalOutput led_;
			private Uart uart_;
			private final int BAUD_RATE = 4800;
			private int nloops = 0;
			//pins
			private final int TX_PIN = 3;
			private final int RX_PIN = 6;
			
			private final int deltat=100;
			@Override
			protected void setup() throws ConnectionLostException,
			InterruptedException {
				Log.i(TAG,"IOIO setup");
				led_ = ioio_.openDigitalOutput(IOIO.LED_PIN);
				uart_ = ioio_.openUart(RX_PIN, TX_PIN, BAUD_RATE, Uart.Parity.NONE, Uart.StopBits.ONE);
				parser = new NMEAParser(uart_.getInputStream());
				led_.write(true);					
			}
			
			@Override
			public void loop() throws ConnectionLostException,
			InterruptedException {
				this.nloops += 1;
				
				Log.d(TAG, String.format("Loop no %d", this.nloops) );
				try {
					parser.handleCharacters();

				} catch (IOException e) {
					Log.e(TAG, e.getMessage());
				}
				synchronized(depths) {
					Log.d(TAG,  String.format("No depths: %d, No positions: %d", depths.size(), positions.size()));
				}
				while (interpolate_pos()) {
					led_.write(true);
				}
				Thread.sleep(this.deltat);
				led_.write(false);			
			}
				
		};
	}
	

	public void onCreate() {
		this.prj = new Project(projectName);
		this.prj.initLogs();
		MIN_ACCURACY_METERS = this.prj.getParameterAsDouble("gps.accuracy");
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		depths = new LinkedList<SonarReading>();
		positions = new CopyOnWriteArrayList<GpsReading>();
		showNotification();
		startGpsListener();
		super.onCreate();		
	}
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		
	}
	
	private void startGpsListener() {

		// ---use the LocationManager class to obtain GPS locations---
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationListener = new MyLocationListener();
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
					MIN_TIME_MILLIS,
					MIN_DISTANCE_METERS, locationListener);
		
		Log.d(TAG, "Started location manager");
	}

	private void shutdownGpsListener() {
		lm.removeUpdates(locationListener);
	}
	
	private void sendUpdateUIBroadcast(){
		Intent intent = new Intent("UPDATE_STATS");
	    LocalBroadcastManager.getInstance(this).sendBroadcastSync(intent);
	}

	private void showNotification() {
		// In this sample, we'll use the same text for the ticker and the
				// expanded notification
		CharSequence text = "Logging sonar!";
				
		// prepare intent which is triggered if the
		// notification is selected

		Intent intent = new Intent(this, SonarLoggerActivity.class);
		PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
				
		// build notification
		// the addAction re-use the same intent to keep the example short
		Notification n  = new NotificationCompat.Builder(this)
				.setContentTitle(text)
				.setContentText("Sonar Logger is running!")
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentIntent(pIntent)
				.addAction(R.drawable.ic_launcher,"Go to activity", pIntent).build();
		
		mNM.notify(0, n);	
	}
	public  void disconnected() {
		Log.d(TAG, "IOIO disconnected!");
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		this.prj.close();
		shutdownGpsListener();
		// Tell the user we stopped.
		Toast.makeText(this, "Sonar Logger stopped!", Toast.LENGTH_SHORT).show();
		mNM.cancel(0);

	}
	
	
	public boolean interpolate_pos() {
		this.sendUpdateUIBroadcast();
		int posInd = 1;
		SonarReading reading;
		synchronized(depths) {
			reading = depths.peek();
			while (reading != null && positions.size() > 0 && reading.timestamp < positions.get(0).timestamp) {
				depths.remove();
				reading = depths.peek();
			}
		}

		// depth queue empty
		if (reading == null)
			return false;
		
		//no positions recorded
		if (positions.size() < 2) {
			return false;
		}
		
		while( posInd < positions.size() - 1 &&
				reading.timestamp > positions.get(posInd).timestamp) {		
			posInd += 1; 
		}

		if (posInd < 1 | posInd >= positions.size())
			return false;
		
		GpsReading p1 = positions.get(posInd - 1);
		GpsReading p2 = positions.get(posInd);
	

		if (reading.timestamp > p2.timestamp | reading.timestamp < p1.timestamp)
			return false;

		if (distance(p1, p2) > MAX_INTERPOLATION_DIST) {
			Log.d(TAG, "Too large distance between positions");
			synchronized(depths) {
				depths.remove();
			}
			this.remove_old_positions(posInd -1);
			return true;
		}
		
		if (reading.timestamp == p1.timestamp)
			reading.set_pos(p1.longitude, p1.latitude, p1.accuracy);
		else if (reading.timestamp == p2.timestamp)
			reading.set_pos(p2.longitude, p2.latitude, p2.accuracy);
		else {
			double weight = ((reading.timestamp - p1.timestamp) /
					(p2.timestamp-p1.timestamp));
			reading.set_pos(
					p1.longitude + weight * (p2.longitude - p1.longitude),
					p1.latitude + weight * (p2.latitude - p1.latitude),
					Math.max(p1.accuracy, p2.accuracy)
			);
		}

		this.remove_old_positions(posInd -1);
		this.prj.log_depth(reading);
		synchronized(depths) {
			depths.remove();
		}
		SonarLoggerActivity.logged_depths += 1;

		return true;
	}
	
	public double distance(GpsReading p1, GpsReading p2) {
		double R = 6371; // km
		double phi_1 = Math.toRadians(p1.latitude);
		double phi_2 = Math.toRadians(p2.latitude);
		double delta_phi = Math.toRadians(p2.latitude - p1.latitude);
		double delta_lambda = Math.toRadians(p2.longitude - p1.longitude);

		double a = Math.sin(delta_phi/2) * Math.sin(delta_phi/2) +
		        Math.cos(phi_1) * Math.cos(phi_2) *
		        Math.sin(delta_lambda/2) * Math.sin(delta_lambda/2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		double dist = R * c;
		SonarLoggerActivity.distance = dist;
		return dist;
	}
	
	 public void remove_old_positions(int lastOldIndex) {
		 positions = new CopyOnWriteArrayList<GpsReading>(positions.subList(lastOldIndex, positions.size()));
	 }
	
	private class MyLocationListener implements LocationListener {
		
		public void onLocationChanged(Location loc) {
			if (loc != null) {
				try { 
					SonarLoggerActivity.tot_pos += 1;

					if (loc.hasAccuracy() && (loc.getAccuracy() <= MIN_ACCURACY_METERS)) {
						GpsReading reading = new GpsReading(
								loc.getLongitude(),
								loc.getLatitude(),
								System.currentTimeMillis(),
								(double) loc.getAccuracy());
						positions.add(reading);
						SonarLoggerActivity.valid_pos += 1;
						SonarLoggerActivity.accuracy = loc.getAccuracy();
						
					}
				} catch (Exception e) {
					Log.e(TAG, e.toString());
				}
			}
		}
						
		public void onProviderDisabled(String provider) {
			if (showingDebugToast)
				Toast.makeText(getBaseContext(),
						"onProviderDisabled: " + provider, Toast.LENGTH_SHORT)
						.show();

		}

		public void onProviderEnabled(String provider) {
			if (showingDebugToast)
				Toast.makeText(getBaseContext(),
						"onProviderEnabled: " + provider, Toast.LENGTH_SHORT)
						.show();

		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			String showStatus = null;
			if (status == LocationProvider.AVAILABLE)
				showStatus = "Available";
			if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
				showStatus = "Temporarily Unavailable";
			if (status == LocationProvider.OUT_OF_SERVICE)
				showStatus = "Out of Service";
			if (status != lastStatus && showingDebugToast) {
				Toast.makeText(SonarReader.this, "new status: " + showStatus,
						Toast.LENGTH_SHORT).show();
			}
			lastStatus = status;
		}

	}

	
	@Override
	public IBinder onBind(Intent arg0) {
		return ioioBinder;
	}

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder ioioBinder = new LocalBinder();

	/*
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		public SonarReader getService() {
			return SonarReader.this;
		}
	}
	
}
