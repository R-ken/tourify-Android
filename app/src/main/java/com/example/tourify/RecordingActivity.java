package com.example.tourify;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class RecordingActivity extends AppCompatActivity {

    public static final int REQUEST_CODE = 359;

    private String mEventId;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    CollectionReference coordinatesRef;

    // Access token is need to successfully run. For more info checkout: https://www.mapbox.com/
    private String accessToken = "pk.eyJ1IjoiamFja3lqcyIsImEiOiJjazZjcjNndDAxZXo2M25wanVqNng1MDNsIn0.W3EnhJe_JOD0Cg9OBeTghA";

    // Location
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private FusedLocationProviderClient mFusedLocationClient;
    Location startLocation;
    Location lastLocation;

    double distanceTravelled = 0.0;     //km
    double averageSpeed = 0.0;          //km/hr
    boolean isRecording = false;

    // views
    Button buttonRecord;
    Button buttonExit;
    TextView textViewCurrentSpeed;
    TextView textViewAverageSpeed;
    TextView textViewTimeElapsed;
    TextView textViewDistanceTravelled;
    TextView labelCurrentSpeed;
    TextView labelDistanceTravelled;
    TextView labelTimeElapsed;
    TextView labelAverageSpeed;
    ConstraintLayout layoutBackground;

    DateFormat outputFormat;

    // mapbox
    private MapboxMap mapboxMap;
    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, accessToken);
        setContentView(R.layout.activity_recording);

        // formatter for elapsed time
        outputFormat = new SimpleDateFormat("HH:mm:ss");
        outputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        //set up views
        buttonRecord = findViewById(R.id.buttonRecord);
        buttonExit = findViewById(R.id.buttonExit);
        textViewCurrentSpeed = findViewById(R.id.textViewCurrentSpeed);
        textViewAverageSpeed = findViewById(R.id.textViewAverageSpeed);
        textViewTimeElapsed = findViewById(R.id.textViewTimeElasped);
        textViewDistanceTravelled = findViewById(R.id.textViewDistanceTravelled);
        labelCurrentSpeed = findViewById(R.id.labelCurrentSpeed);
        labelDistanceTravelled = findViewById(R.id.labelDistanceTravelled);
        labelTimeElapsed = findViewById(R.id.labelTimeElapsed);
        labelAverageSpeed = findViewById(R.id.labelAverageSpeed);
        buttonRecord.setOnClickListener(new RecordOnClickListener());
        buttonExit.setOnClickListener(new ExitOnClickListener());
        layoutBackground = findViewById(R.id.layoutBackground);

        /* Get bundled information about a ride passed in from HomeActivity */
        Bundle bundle = getIntent().getExtras();
        mEventId = bundle.getString(Constants.EVENT_ID);

        /* Firebase */
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        String currentUserUUID = auth.getCurrentUser().getUid();
        coordinatesRef = db.collection("events").document(mEventId).collection("coordinates");

        /**
         * Note on GPS current speed: https://en.wikipedia.org/wiki/Speedometer#GPS
         * Take away: Depends on satellite
         */
        //set up location services
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(5000);
        mLocationCallback = new MyLocationCallback();

        //check permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_CODE
            );
        }

        //set up mapbox
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                RecordingActivity.this.mapboxMap = mapboxMap;
                mapboxMap.getUiSettings().setAllGesturesEnabled(false);
                mapboxMap.setStyle(Style.LIGHT, new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {

                        // Get an instance of the component
                        LocationComponent locationComponent = RecordingActivity.this.mapboxMap.getLocationComponent();

                        // Set the LocationComponent activation options
                        LocationComponentActivationOptions locationComponentActivationOptions =
                                LocationComponentActivationOptions.builder(RecordingActivity.this, style)
                                        .useDefaultLocationEngine(false)
                                        .build();

                        // Activate with the LocationComponentActivationOptions object
                        locationComponent.activateLocationComponent(locationComponentActivationOptions);

                        // Enable to make component visible
                        locationComponent.setLocationComponentEnabled(true);

                        // Set the component's camera mode
                        locationComponent.setCameraMode(CameraMode.TRACKING_GPS_NORTH);

                        // Set the component's render mode
                        locationComponent.setRenderMode(RenderMode.NORMAL);
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 1000) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Lifecycle methods
     */
    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    /**
     * Unregister sensors and GPS listeners
     */
    @Override
    protected void onPause() {
        super.onPause();

        mapView.onPause();

        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    /**
     * Unregister sensors and GPS listeners
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    /*
        Exit back to MainActivity
     */
    private class ExitOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            finish();
        }
    }

    private class RecordOnClickListener implements View.OnClickListener {

        /**
         * Start / stop recording based on current recording state.
         *
         * @param view
         */
        @Override
        public void onClick(View view) {
            // do not proceed if location client is null
            if (mFusedLocationClient == null) {
                return;
            }

            if (isRecording) {
                // stop recording
                buttonRecord.setText("Start Tracking");
                isRecording = false;
                mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            } else {
                // start recording
                // reset text views
                buttonRecord.setText("Stop Tracking");
                textViewAverageSpeed.setText("0 km/hr");
                textViewCurrentSpeed.setText("0 km/hr");
                textViewDistanceTravelled.setText("0.0 km");
                startLocation = null;
                lastLocation = null;
                isRecording = true;

                //start tracking location
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
            }
        }
    }

    /**
     * Call back for each successful location update
     */
    private class MyLocationCallback extends LocationCallback {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);

            String currentSpeedText;
            String averageSpeedText;
            String distanceTravelledText;
            String timeElapsedText;

            if (locationResult == null) {
                return;
            }

            for (Location location : locationResult.getLocations()) {
                // null location, do not proceed
                if (location == null) {
                    continue;
                }

                // start location
                if (startLocation == null) {
                    startLocation = location;
                }

                if (lastLocation == null) {
                    lastLocation = location;
                    continue;
                }

                // only update location if >= 3 seconds has elapsed
                long timeDiff = location.getTime() - lastLocation.getTime();
                if (timeDiff < 3000) {
                    continue;
                }

                // update distance travelled
                distanceTravelled += lastLocation.distanceTo(location);
                distanceTravelledText = Math.round(distanceTravelled / 100.0) / 10.0 + " km";

                //update last location recorded
                lastLocation = location;

                // update time elapsed
                long elapsedTime = lastLocation.getTime() - startLocation.getTime();
                timeElapsedText = outputFormat.format(elapsedTime);

                // update average speed
                double elapsedTimeSeconds = elapsedTime / 1000;
                double metersPerSecond = distanceTravelled / elapsedTimeSeconds;
                averageSpeed = metersPerSecond * 3.6;
                averageSpeedText = Math.round(averageSpeed * 10.0) / 10.0 + " km/h";

                //update current speed
                currentSpeedText = Math.round(location.getSpeed() * 3.6 * 10.0) / 10.0 + " km/h";

                //update text on app screen
                textViewTimeElapsed.setText(timeElapsedText);
                textViewAverageSpeed.setText(averageSpeedText);
                textViewCurrentSpeed.setText(currentSpeedText);
                textViewDistanceTravelled.setText(distanceTravelledText);

                //update location on map
                if (mapboxMap.getLocationComponent().isLocationComponentEnabled() && mapboxMap.getLocationComponent().isLocationComponentActivated()) {
                    mapboxMap.getLocationComponent().forceLocationUpdate(lastLocation);
                }

                //send data to firebase
                Map<String, Object> newData = new HashMap<>();
                newData.put("user_uuid", auth.getCurrentUser().getUid());
                newData.put("latlng", new GeoPoint(location.getLatitude(), location.getLongitude()));
                newData.put("timestamp", new Timestamp(lastLocation.getTime() / 1000, 0));
                coordinatesRef.add(newData);
            }
        }
    }
}
