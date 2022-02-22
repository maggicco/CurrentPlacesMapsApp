package com.maggicco.mapsapp;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An activity that displays a map showing the place at the device's current location.
 */
public class MapsActivityCurrentPlace extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final String TAG = MapsActivityCurrentPlace.class.getSimpleName();
    private GoogleMap map;
    private CameraPosition cameraPosition;

    // The entry point to the Places API.
    private PlacesClient placesClient;

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient fusedLocationProviderClient;

    // A default location (Paris, France) and default zoom to use when location permission is
    // not granted.
    private final LatLng defaultLocation = new LatLng(-48.51529776, 2.20564504);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean locationPermissionGranted;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location lastKnownLocation;

    // Keys for storing activity state.
    // [START maps_current_place_state_keys]
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    // Used for selecting the current place.
    private static final int M_MAX_ENTRIES = 5;
    private String[] likelyPlaceNames;
    private String[] likelyPlaceAddresses;
    private List[] likelyPlaceAttributions;
    private LatLng[] likelyPlaceLatLngs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // [START_EXCLUDE silent]
        // [START maps_current_place_on_create_save_instance_state]
        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps);

        // [START_EXCLUDE silent]
        // Construct a PlacesClient
        Places.initialize(getApplicationContext(), BuildConfig.MAPS_API_KEY);
        placesClient = Places.createClient(this);

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Build the map.
        // [START maps_current_place_map_fragment]
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (map != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, map.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, lastKnownLocation);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Sets up the options menu.
     * @param menu The options menu.
     * @return Boolean.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.current_place_menu, menu);
        return true;
    }

    /**
     * Handles a click on the menu option to get a place.
     * @param item The menu item to handle.
     * @return Boolean.
     */
    // [START maps_current_place_on_options_item_selected]
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.option_get_place) {
            showCurrentPlace();
        }
        return true;
    }

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        this.map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout) findViewById(R.id.map), false);

                TextView title = infoWindow.findViewById(R.id.title);
                title.setText(marker.getTitle());

                TextView snippet = infoWindow.findViewById(R.id.snippet);
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Prompt the user for permission.
        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) {
                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(lastKnownLocation.getLatitude(),
                                                lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            map.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                            map.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    /**
     * Prompts the user for permission to use the device location.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        locationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    /**
     * Prompts the user to select the current place from a list of likely places, and shows the
     * current place on the map - provided the user has granted location permission.
     */
    private void showCurrentPlace() {
        if (map == null) {
            return;
        }
        if(locationPermissionGranted){

            // Use fields to define the data types to return.
            //List<Place.Field> placeFields = Collections.singletonList(Place.Field.NAME);
            List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME,Place.Field.TYPES, Place.Field.ID,
                    Place.Field.LAT_LNG);

            // Use the builder to create a FindCurrentPlaceRequest.
            FindCurrentPlaceRequest placeRequest = FindCurrentPlaceRequest.builder(placeFields).build();

            // Call findCurrentPlace and handle the response (first check that the user has granted permission).
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Task<FindCurrentPlaceResponse> placeResponse = placesClient.findCurrentPlace(placeRequest);
                placeResponse.addOnCompleteListener(task -> {
                    if (task.isSuccessful()){
                        FindCurrentPlaceResponse response = task.getResult();
                        for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {

                            if (placeLikelihood.getPlace().getTypes() != null &&
                                    (placeLikelihood.getPlace().getTypes().contains(Place.Type.RESTAURANT)
                                            || placeLikelihood.getPlace().getTypes().contains(Place.Type.BAR) ||
                                            placeLikelihood.getPlace().getTypes().contains(Place.Type.MEAL_TAKEAWAY))) {

                                    Log.i(TAG, String.format("Place '%s' has likelihood: %f",
                                            placeLikelihood.getPlace().getName(),
                                            placeLikelihood.getLikelihood()));

                                    map.addMarker(new MarkerOptions()
                                            .position(placeLikelihood.getPlace().getLatLng())
                                            .title((String) placeLikelihood.getPlace().getName())
                                            .snippet((String)
                                                    placeLikelihood.getPlace().getPhoneNumber()));


                            }
                        }
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof ApiException) {
                            ApiException apiException = (ApiException) exception;
                            Log.e(TAG, "Place not found: " + apiException.getStatusCode());
                        }
                    }
                });
            } else {
                // A local method to request required permissions;
                // See https://developer.android.com/training/permissions/requesting
                getLocationPermission();
            }

        }

        if (locationPermissionGranted) {
            // Use fields to define the data types to return.
            List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS,
                    Place.Field.LAT_LNG);

            // Use the builder to create a FindCurrentPlaceRequest.
            FindCurrentPlaceRequest request =
                    FindCurrentPlaceRequest.newInstance(placeFields);

            // Get the likely places - that is, the businesses and other points of interest that
            // are the best match for the device's current location.
            @SuppressWarnings("MissingPermission") final
            Task<FindCurrentPlaceResponse> placeResult =
                    placesClient.findCurrentPlace(request);
            placeResult.addOnCompleteListener (new OnCompleteListener<FindCurrentPlaceResponse>() {
                @Override
                public void onComplete(@NonNull Task<FindCurrentPlaceResponse> task) {
                    if (task.isSuccessful() && task.getResult() != null) {
                        FindCurrentPlaceResponse likelyPlaces = task.getResult();

                        // Set the count, handling cases where less than 5 entries are returned.
                        int count;
                        if (likelyPlaces.getPlaceLikelihoods().size() < M_MAX_ENTRIES) {
                            count = likelyPlaces.getPlaceLikelihoods().size();
                        } else {
                            count = M_MAX_ENTRIES;
                        }

                        int i = 0;
                        likelyPlaceNames = new String[count];
                        likelyPlaceAddresses = new String[count];
                        likelyPlaceAttributions = new List[count];
                        likelyPlaceLatLngs = new LatLng[count];

                        for (PlaceLikelihood placeLikelihood : likelyPlaces.getPlaceLikelihoods()) {
                            // Build a list of likely places to show the user.
                            likelyPlaceNames[i] = placeLikelihood.getPlace().getName();
                            likelyPlaceAddresses[i] = placeLikelihood.getPlace().getAddress();
                            likelyPlaceAttributions[i] = placeLikelihood.getPlace()
                                    .getAttributions();
                            likelyPlaceLatLngs[i] = placeLikelihood.getPlace().getLatLng();

                            i++;
                            if (i > (count - 1)) {
                                break;
                            }
                        }

                        // Show a dialog offering the user the list of likely places, and add a
                        // marker at the selected place.
                        MapsActivityCurrentPlace.this.openPlacesDialog();
                    }
                    else {
                        Log.e(TAG, "Exception: %s", task.getException());
                    }
                }
            });
        } else {
            // The user has not granted permission.
            Log.i(TAG, "The user did not grant location permission.");

            // Add a default marker, because the user hasn't selected a place.
            map.addMarker(new MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(defaultLocation)
                    .snippet(getString(R.string.default_info_snippet)));

            // Prompt the user for permission.
            getLocationPermission();
        }
    }

    /**
     * Displays a form allowing the user to select a place from a list of likely places.
     */
    private void openPlacesDialog() {
        // Ask the user to choose the place where they are now.
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The "which" argument contains the position of the selected item.
                LatLng markerLatLng = likelyPlaceLatLngs[which];
                String markerSnippet = likelyPlaceAddresses[which];
                if (likelyPlaceAttributions[which] != null) {
                    markerSnippet = markerSnippet + "\n" + likelyPlaceAttributions[which];
                }

                // Add a marker for the selected place, with an info window
                // showing information about that place.
                map.addMarker(new MarkerOptions()
                        .title(likelyPlaceNames[which])
                        .position(markerLatLng)
                        .snippet(markerSnippet));

                // Position the map's camera at the location of the marker.
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,
                        DEFAULT_ZOOM));
            }
        };

        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pick_place)
                .setItems(likelyPlaceNames, listener)
                .show();
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (map == null) {
            return;
        }
        try {
            if (locationPermissionGranted) {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                map.setMyLocationEnabled(false);
                map.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }
}

//
//package com.maggicco.go4lunch.ui;
//
//        import androidx.annotation.NonNull;
//        import androidx.annotation.Nullable;
//        import androidx.core.app.ActivityCompat;
//        import androidx.core.content.ContextCompat;
//        import androidx.fragment.app.Fragment;
//
//        import android.Manifest;
//        import android.annotation.SuppressLint;
//        import android.app.Activity;
//        import android.content.Context;
//        import android.content.pm.PackageManager;
//        import android.graphics.Bitmap;
//        import android.location.Location;
//        import android.location.LocationListener;
//        import android.location.LocationManager;
//        import android.os.Bundle;
//        import android.view.LayoutInflater;
//        import android.view.Menu;
//        import android.view.MenuInflater;
//        import android.view.View;
//        import android.view.ViewGroup;
//        import android.widget.SearchView;
//        import android.widget.Toast;
//
//        import com.google.android.gms.location.FusedLocationProviderClient;
//        import com.google.android.gms.location.LocationServices;
//        import com.google.android.gms.maps.CameraUpdateFactory;
//        import com.google.android.gms.maps.GoogleMap;
//        import com.google.android.gms.maps.MapView;
//        import com.google.android.gms.maps.OnMapReadyCallback;
//        import com.google.android.gms.maps.SupportMapFragment;
//        import com.google.android.gms.maps.model.BitmapDescriptorFactory;
//        import com.google.android.gms.maps.model.LatLng;
//        import com.google.android.gms.maps.model.MarkerOptions;
//        import com.google.android.libraries.places.api.Places;
//        import com.google.android.libraries.places.api.model.Place;
//        import com.google.android.libraries.places.api.model.PlaceLikelihood;
//        import com.google.android.libraries.places.api.net.FetchPlaceRequest;
//        import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
//        import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
//        import com.google.android.libraries.places.api.net.PlacesClient;
//        import com.maggicco.go4lunch.BuildConfig;
//        import com.maggicco.go4lunch.R;
//
//        import org.jetbrains.annotations.NotNull;
//
//        import java.util.ArrayList;
//        import java.util.Arrays;
//        import java.util.List;
//        import java.util.Objects;
//
//public class MapsViewFragment extends Fragment implements OnMapReadyCallback{
//
//    private static final int LOCATION_MIN_UPDATE_TIME = 10;
//    private static final int LOCATION_MIN_UPDATE_DISTANCE = 1000;
//    private Location location = null;
//    private GoogleMap googleMap;
//    private LocationManager locationManager;
//    private PlacesClient placesClient;
//    private MapView mapView;
//    private List<String> idList;
//    private Activity activity;
//    private FusedLocationProviderClient fusedLocationProviderClient;
//    private boolean locationPermissionGranted;
//    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
//
//
//    private List<String> userList;
//    private List<Place> placeList;
//
//    public MapsViewFragment(){
//
//    }
//
//    @Nullable
//    @Override
//    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
//        View view = inflater.inflate(R.layout.fragment_maps_view, container, false);
//        setHasOptionsMenu(true);
//        return view;
//    }
//
//    @Override
//    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
//        super.onViewCreated(view, savedInstanceState);
//        ((LoggedInActivity)getActivity()).setToolbarNavigation();
//        ((LoggedInActivity) getActivity()).getSupportActionBar().setTitle("I'm hungry");
//        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
//        mapFragment.getMapAsync(this);
//        locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
//        activity = getActivity();
//        idList = new ArrayList<>();
//        placeList = new ArrayList<>();
//        //initMapAndPlaces();
//        //initView(savedInstanceState);
//
//    }
//
//    @Override
//    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
//        super.onCreateOptionsMenu(menu, inflater);
//        inflater.inflate(R.menu.search_menu, menu);
//    }
//
//
//    @Override
//    public void onMapReady(GoogleMap googleMap) {
//        this.googleMap = googleMap;
//
//        initMap();
//        getCurrentLocation();
//
//        fetchPlacesAroundUser();
//        addMarkerOnMap();
//
//
//
//    }
//
//
//    private LocationListener locationListener = new LocationListener() {
//        @Override
//        public void onLocationChanged(Location location) {
//            drawMarker(location, getText(R.string.i_am_here).toString());
//            locationManager.removeUpdates(locationListener);
//        }
//
//        @Override
//        public void onStatusChanged(String s, int i, Bundle bundle) {
//
//        }
//
//        @Override
//        public void onProviderEnabled(String s) {
//
//        }
//
//        @Override
//        public void onProviderDisabled(String s) {
//
//        }
//    };
//
//
//
//    /**
//     * Init the map's UI settings based on whether the user has granted location permission.
//     */
//    private void initMap() {
//        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            if (googleMap != null) {
//                googleMap.setMyLocationEnabled(true);
//                googleMap.getUiSettings().setMyLocationButtonEnabled(true);
//                googleMap.getUiSettings().setAllGesturesEnabled(true);
//                googleMap.getUiSettings().setZoomControlsEnabled(true);
//
//            }
//        } else {
//            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 12);
//            }
//            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 13);
//            }
//        }
//    }
//    /**
//     * Prompts the user for permission to use the device location.
//     */
//    private void getLocationPermission() {
//        /*
//         * Request location permission, so that we can get the location of the
//         * device. The result of the permission request is handled by a callback,
//         * onRequestPermissionsResult.
//         */
//        if (ContextCompat.checkSelfPermission(this.getContext(),
//                android.Manifest.permission.ACCESS_FINE_LOCATION)
//                == PackageManager.PERMISSION_GRANTED) {
//            locationPermissionGranted = true;
//
//        } else {
//            ActivityCompat.requestPermissions(this.getActivity(),
//                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
//                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
//        }
//    }
//
//
//    private void getCurrentLocation() {
//        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
//            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
//            if (!isGPSEnabled && !isNetworkEnabled) {
//                Toast.makeText(getContext(), "GPS or Network problem!", Toast.LENGTH_LONG).show();
//            } else {
//                location = null;
//                if (isGPSEnabled) {
//                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_MIN_UPDATE_TIME, LOCATION_MIN_UPDATE_DISTANCE, locationListener);
//                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//                }
//                if (isNetworkEnabled) {
//                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_MIN_UPDATE_TIME, LOCATION_MIN_UPDATE_DISTANCE, locationListener);
//                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
//                }
//                if (location != null) {
//                    drawMarker(location, getText(R.string.i_am_here).toString());
//                }
//            }
//        } else {
//            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 12);
//            }
//            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 13);
//            }
//        }
//
//    }
//
//    /**
//     * Fetches places around the user's location into GoogleMaps server
//     */
//    @SuppressLint("MissingPermission")
//    private void fetchPlacesAroundUser() {
////        Defines witch fields we want in the query's response.
//        List<Place.Field> fields = Arrays.asList(Place.Field.TYPES, Place.Field.ID);
////        Creates the request with the defined fields.
//        FindCurrentPlaceRequest placeRequest = FindCurrentPlaceRequest.builder(fields).build();
////        Requests GoogleMap's server to fetch places around user
//        placesClient.findCurrentPlace(placeRequest).addOnCompleteListener(task -> {
//            if (task.isSuccessful() && task.getResult() != null) {
//                FindCurrentPlaceResponse response = task.getResult();
//                for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
////                        Verifies if the place's type corresponding to restaurants.
//                    if (placeLikelihood.getPlace().getTypes() != null &&
//                            (placeLikelihood.getPlace().getTypes().contains(Place.Type.RESTAURANT)
//                                    || placeLikelihood.getPlace().getTypes().contains(Place.Type.BAR) ||
//                                    placeLikelihood.getPlace().getTypes().contains(Place.Type.MEAL_TAKEAWAY))) {
////                        Adds each place's id in a list to pass it to HomeActivity
//                        idList.add(placeLikelihood.getPlace().getId());
////                        Requests details for each place corresponding to wanted types.
//                        fetchDetailsAboutRestaurants(placeLikelihood.getPlace().getId());
//                    }
//                }
////                Sends the mIdList to HomeActivity for create a restaurant list into RestaurantFragment.
//                //mListener.onFragmentInteraction(idList);
//            }
//        });
//    }
//
//    /**
//     * Fetches place's details according to the place's Id passed in argument.
//     *
//     * @param placeId The place's id that we want details.
//     */
//    private void fetchDetailsAboutRestaurants(String placeId) {
////        Defines witch fields we want in the query's response.
//        List<Place.Field> fields = Arrays.asList(Place.Field.ID,Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS,
//                Place.Field.ADDRESS_COMPONENTS, Place.Field.PHONE_NUMBER, Place.Field.WEBSITE_URI,
//                Place.Field.OPENING_HOURS, Place.Field.RATING);
////        Creates the request with the defined fields about the given place.
//        FetchPlaceRequest placeRequest = FetchPlaceRequest.builder(placeId, fields).build();
////        Requests GoogleMap's server to fetch place's details.
//        placesClient.fetchPlace(placeRequest).addOnCompleteListener(task -> {
//            if (task.isSuccessful() && task.getResult() != null) {
//                Place place = (task.getResult()).getPlace();
//                placeList.add(place);
//                //saveRestaurantInFirestore(placeId, place);
//            }
//            if (placeList.size() == idList.size())
//                addMarkerOnMap();
//        });
//    }
//
////    /**
////     * Fetches place's details according to the place's Id passed in argument.
////     *
////     * @param placeId The place's id that we want details.
////     */
////    private void fetchDetailsAboutRestaurants(String placeId) {
//////        Defines witch fields we want in the query's response.
////        List<Place.Field> fields = Arrays.asList(Place.Field.ID,Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS,
////                Place.Field.ADDRESS_COMPONENTS, Place.Field.PHONE_NUMBER, Place.Field.WEBSITE_URI,
////                Place.Field.OPENING_HOURS, Place.Field.RATING);
//////        Creates the request with the defined fields about the given place.
////        FetchPlaceRequest placeRequest = FetchPlaceRequest.builder(placeId, fields).build();
//////        Requests GoogleMap's server to fetch place's details.
////        placesClient.fetchPlace(placeRequest).addOnCompleteListener(task -> {
////            if (task.isSuccessful() && task.getResult() != null) {
////                Place place = (task.getResult()).getPlace();
////                placeList.add(place);
////
////            }
////            if placeList.size() == idList.size())
////                addMarkerOnMap();
////        });
////    }
//
//
//    /**
//     * Adds a marker on map according to the place's location and the place's name.
//     */
//    private void addMarkerOnMap() {
//        if(googleMap != null)
//            googleMap.clear();
//        if (placeList != null && ! placeList.isEmpty()) {
//            for (Place place : placeList) {
//                int count = 0;
////                for (User user : mUserList) {
////                    if (user.getRestaurantId() != null && user.getRestaurantId().equals(place.getId())) {
////                        count++;
////                    }
////                }
//                if (place.getLatLng() != null) {
//                    if (count > 0)
//                        googleMap.addMarker(new MarkerOptions().position(place.getLatLng()).snippet(place.getId())
//                                .title(place.getName()).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_location_red_on_24)));
////                    else
////                        googleMap.addMarker(new MarkerOptions().position(place.getLatLng()).snippet(place.getId())
////                                .title(place.getName()).icon(BitmapDescriptorFactory.fromBitmap(mBitmapOrange)));
//                }
//            }
//        }
//    }
//
//    private void drawMarker(Location location, String title) {
//        if (this.googleMap != null) {
//            googleMap.clear();
//            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
//            MarkerOptions markerOptions = new MarkerOptions();
//            markerOptions.position(latLng);
//            markerOptions.title(title);
//            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
//            googleMap.addMarker(markerOptions);
//            googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
//            googleMap.animateCamera(CameraUpdateFactory.zoomTo(12));
//        }
//    }
//
//
//
//}
