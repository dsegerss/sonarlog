package org.sonarlog.ioio;

public class SonarReading {
	public double depth;
	public long timestamp;
	public double longitude;
	public double latitude;
	public double accuracy;
	public SonarReading(double d, long ts) {
		depth = d;
		timestamp = ts;
	}
	
	public void set_pos(double lon, double lat, double accuracy) {
		this.longitude = lon;
		this.latitude = lat;
		this.accuracy = accuracy;
	}	
}
