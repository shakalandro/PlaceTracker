package com.cse561.PlaceTracker;
/*
 * Stolen from: http://android-developers.blogspot.com/2011/06/deep-dive-into-location.html
 */
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

/**
 * This Receiver class is used to listen for Broadcast Intents that announce
 * that a location change has occurred. This is used instead of a LocationListener
 * within an Activity is our only action is to start a service.
 */
public class LocationUpdateReceiver extends BroadcastReceiver {
  
  protected static String TAG = "LocationUpdateReceiver";
  
  /**
   * This is the Active receiver, used to receive Location updates when 
   * the Activity is visible. 
   */
  
  @Override
  public void onReceive(Context context, Intent intent) {
    String locationKey = LocationManager.KEY_LOCATION_CHANGED;
    if (intent.hasExtra(locationKey)) {
      Location location = (Location)intent.getExtras().get(locationKey);
      PlaceTracker.GotLocationUpdate(location);
    }  
  }
} 
