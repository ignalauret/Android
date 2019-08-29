/*
 *
 * @author Ignacio Lauret <ignalauret@gmail.com>
 *
 */

package ignalau.appauto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.Task;

import java.util.List;

import static ignalau.appauto.Constants.LOCATION_REQUEST_FASTEST_INTERVAL;
import static ignalau.appauto.Constants.LOCATION_REQUEST_INTERVAL;
import static ignalau.appauto.Constants.REQUEST_LOCATION_CODE;

/** This module is in charge of checking if the GPS is enabled in the clients phone, if not it asks
 * the client to enable it. If the client enables the GPS location, we continue with our Main, if
 * not, we go to our "You can't use this app without GPS enabled" screen.
 */
public class Geolocation extends AppCompatActivity {


    private GoogleApiClient googleApiClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* We want to exit the Alert if the client touches outside */
        this.setFinishOnTouchOutside(true);

        checkGeolocationActive();
    }


    /** Checks if the GPS location is active, if not it calls the func that asks for it.*/
    private void checkGeolocationActive(){
        final LocationManager manager =
                (LocationManager) Geolocation.this.getSystemService(Context.LOCATION_SERVICE);

        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    hasGPSDevice(Geolocation.this)) {
            finish();
        }

        if(!hasGPSDevice(Geolocation.this)){
            /* No GPS supported on the phone... Should never happen */
            Toast.makeText(Geolocation.this,"No GPS",Toast.LENGTH_SHORT).show();
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    hasGPSDevice(Geolocation.this)) {
            enableLoc();
        }
    }


    /** Checks if the phone has GPS available. Should allways return true */
    private boolean hasGPSDevice(Context context) {
        final LocationManager mgr =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (mgr == null)
            return false;
        final List<String> providers = mgr.getAllProviders();
        if (providers == null)
            return false;
        return providers.contains(LocationManager.GPS_PROVIDER);
    }

    /** Asks the client to enable the GPS location of the phone */
    private void enableLoc() {
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(Geolocation.this)
                                      .addApi(LocationServices.API)
                                      .addConnectionCallbacks(
                                              new GoogleApiClient.ConnectionCallbacks() {
                                          @Override
                                          public void onConnected(Bundle bundle) {}

                                          @Override
                                          public void onConnectionSuspended(int i) {
                                              googleApiClient.connect();
                                          }
                                      })
                                      .addOnConnectionFailedListener(connectionResult ->
                                            Log.d("Location error","Location error " +
                                            connectionResult.getErrorCode())).build();

            googleApiClient.connect();


            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(LOCATION_REQUEST_INTERVAL);
            locationRequest.setFastestInterval(LOCATION_REQUEST_FASTEST_INTERVAL);
            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                                                              .addLocationRequest(locationRequest);

            /* IMPORTANT */
            builder.setAlwaysShow(true);

            Task<LocationSettingsResponse> task = LocationServices.getSettingsClient(this)
                                                        .checkLocationSettings(builder.build());

            task.addOnCompleteListener(task1 -> {
                try {
                    LocationSettingsResponse response = task1.getResult(ApiException.class);
                    /* If it doesn't throw an exception, we are done here */
                    finish();

                } catch (ApiException exception) {
                    switch (exception.getStatusCode()) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            /* Location settings are not satisfied. But could be fixed by asking
                                the user to change them. */
                            try {
                                ResolvableApiException resolvableException =
                                        (ResolvableApiException) exception;
                                /* Ask with startResolutionForResult and handle it in the
                                    onActivityResult method. */
                                resolvableException.startResolutionForResult(
                                        Geolocation.this,
                                        REQUEST_LOCATION_CODE);

                            } catch (IntentSender.SendIntentException e) {
                                /* Not important */
                            } catch (ClassCastException e) {
                                /* Impossible */
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            /* Location settings can't be changed, so we can't do anything */
                            /* Shouldn't happen */
                            break;
                    }
                }
            });
        }
    }


    /** Handles the response of the user about the GPS location activation. If it has been enabled,
     * it returns to the Main Activity, if not it goes to the "We need your location" activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOCATION_CODE) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    Intent returnMainScreen = new Intent(this, MainScreen.class);
                    startActivity(returnMainScreen);
                    break;
                case Activity.RESULT_CANCELED:
                    /* The user was asked to change settings, but chose not to */
                    Intent notAvailableGps = new Intent(this, NotAvailableGps.class);
                    startActivity(notAvailableGps);
                    break;
                default:
                    break;
            }
        }
    }
}
