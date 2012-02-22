package com.cse561.PlaceTracker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;
 
public class PlaceTracker extends Activity{
	private static final String TAG = "PlaceTracker";
	private static final boolean LOG_TO_FILE = true;
	private static TextView output;                 
	private static LocationManager locationManager; 
  	private static BufferedWriter log_file = null;
  	protected PendingIntent locationListenerPendingIntent;
  	private static Location lastLocation;
  	private static Vector<Place> places = new Vector<Place>();
  	private static int placeCounter = 0; 
  	private static int placeSize = 10;
  	
  	private static final String API_KEY = "AIzaSyAKK_uzNg0jv5XvG1BeUdabcOeXxk4PYMQ";
  	private static final int MAX_API_SEARCH_DIST = 15;		// in meters
  	
  	 
  	//You probably want to refine the definition of a place.
  	public static class Place {
  		private Location loc;
  		private String name;
  		public Place(Location loc, String name) {
  			this.loc = loc;
  			this.name = name;
  		}
  		public Location getLocation() {
  			return loc;
  		}
  		public String getName() {
  			return name;
  		}
  		public String toString() {
  			return name + " (" + loc.getLatitude() + "," + loc.getLongitude() + ")";
  		}
  	}
  	
  	// Given location coordinates returns a list of places nearby in accordance with
  	// http://code.google.com/apis/maps/documentation/places/#PlaceSearchResults
  	// May need to handle exceptions better, not sure how common they are yet
  	public static JSONObject getClosePlaces(double latitude, double longitude) {
		try {
	  		Uri uri = new Uri.Builder()
				.scheme("https")
				.authority("maps.googleapis.com")
				.path("maps/api/place/search/json")
				.appendQueryParameter("key", API_KEY)
				.appendQueryParameter("location", latitude + "," + longitude)
				.appendQueryParameter("radius", MAX_API_SEARCH_DIST + "")
				.appendQueryParameter("sensor", "true")
				.build();
			
	  		URLConnection connection = new URL(uri.toString()).openConnection();
	  		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()), 1024 * 16);
	  		StringBuffer builder = new StringBuffer();
	  		String line;
	  		while ((line = reader.readLine()) != null) {
	  			builder.append(line).append("\n");
	  		}
	  		return new JSONObject(builder.toString());
		} catch (Exception e) {
			return null;
		}
  	}
  	
  	//Called at every location update. 
  	public static void GotLocationUpdate(Location location) {	
  		if (lastLocation != null)
			if (!InTransit(location))
				LogLocation(location, AtPlace(location), false);
			else
				LogLocation(location, "NA", true);
		else
			LogLocation(location, "NA", true);
		lastLocation = location;
		
		JSONObject data = getClosePlaces(location.getLatitude(), location.getLongitude());
		try {
			JSONArray results = data.getJSONArray("results");
			JSONObject firstPlace = results.getJSONObject(0);
			String name = firstPlace.getString("name");
			JSONObject realLoc = firstPlace.getJSONObject("geometry").getJSONObject("location");
			Location loc = new Location("GooglePlacesAPI");
			loc.setLatitude(realLoc.getDouble("lat"));
			loc.setLongitude(realLoc.getDouble("lng"));
			logMessage(new Place(loc, name).toString());
		} catch (JSONException e) {
			logMessage("Malformed JSON");
			Log.e("PlaceTracker", "Malformed JSON: " + data);
		}
  	} 
  	
  	// Determine if you are in motion.
  	// You probably want something more sophisticated than this!
  	public static boolean InTransit(Location loc) {
  		output.append("\nDistance: " + loc.distanceTo(lastLocation));
  		if (loc.distanceTo(lastLocation) > placeSize) {
  			return true;
  		}
  		return false;
  	}
  	
  	// Determine if you are stopped in a place you've been before
  	// You probably want something more sophisticated than this!
  	public static String AtPlace(Location loc) {
  		String place = "";
  		Iterator<Place> it = places.iterator();
  		while (it.hasNext()) {
  			Place nextPlace = it.next();
  			if (loc.distanceTo(nextPlace.getLocation()) < placeSize) {
  				place = nextPlace.getName();
  				break;
  			}
  		}
  		if (place == "") {
  			place = "Place-" + placeCounter++;
  			places.add(new Place(loc, place));
  		}
  		return place;
  	}
  	
	//Called when application starts
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Intent activeIntent = new Intent(this, LocationUpdateReceiver.class);
	    locationListenerPendingIntent = PendingIntent.getBroadcast(this, 0, activeIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		//Set up UI 
		super.onCreate(savedInstanceState);
		output = new TextView(this);
		output.setText("PlaceTracker Started\n");
		output.setMovementMethod(new ScrollingMovementMethod());
	    setContentView(output);	
	       
	    //Initialize WIFI to on
	  	WifiManager wifiManager = (WifiManager)getBaseContext().getSystemService(Context.WIFI_SERVICE);
	  	wifiManager.setWifiEnabled(true);
	  		
		// Set up the location manager and listeners
	    locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);	
	    locationManager.requestLocationUpdates(0, 0, new Criteria(), locationListenerPendingIntent);
	    
		//Set up logging
		if (LOG_TO_FILE) {
			try {
				log_file = new BufferedWriter(new FileWriter(Environment.getExternalStorageDirectory()+ "/log_file.txt", true));
			}
			catch (Exception e) {
				Log.e(TAG, "Could not open log file.\n");
			}
		}
		logMessage("PlaceTracker started - " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime()));
		logMessage("Time, Lat, Long, Accuracy, Place, InMotion");
	} 

	//Use this function to write a location to the log file
	//Logfile format: time (ms), latitude, longitude, accuracy, place, inmotion
	private static void LogLocation(Location location, String place, boolean inMotion) {
		if (inMotion) {
			place = "NA";
		}
		output.append("\n" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(location.getTime()));
		output.append("\n" + location.getProvider());
		output.append("\n" + location.getLatitude() + ", " + location.getLongitude() + 
					", " + location.getAccuracy() + ", " + place + ", " + inMotion);
		logMessage(location.getTime() + ", " + location.getLatitude() + ", " + location.getLongitude() + 
					", " + location.getAccuracy() + ", " + place + ", " + inMotion);
	}
	
	private static void printLocation(Location location) {
		if (location == null)
			output.append("\nLocation[unknown]\n\n");
		else
			output.append("\n" + location.toString());
		}
	
	private static void logMessage(String msg) {
		if (log_file != null) {
			try {
				log_file.write(msg +"\n");
				log_file.flush();
			}
			catch (Exception e) {
				Log.e(TAG, "Could not write to file.\n");
			}		
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume(); 
	}

	@Override
	protected void onPause() {
		super.onPause();
	}
}