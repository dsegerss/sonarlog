package org.sonarlog.ioio;

public class GpsReading {
	public Double longitude;
	public Double latitude;
	public Long timestamp;
	public double accuracy;
	public GpsReading(double lon, double lat, Long ts, double acc) {
		this.longitude = lon;
		this.latitude = lat;
		this.timestamp = ts;
		this.accuracy = acc;
	}
}
