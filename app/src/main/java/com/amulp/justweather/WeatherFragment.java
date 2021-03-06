package com.amulp.justweather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v4.app.Fragment;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.amulp.justweather.data.Temperature;
import com.google.gson.Gson;
import com.amulp.justweather.data.WeatherObj;
import com.amulp.justweather.modules.DownloadTask;

import java.sql.Date;
import java.text.SimpleDateFormat;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.support.v4.content.PermissionChecker.checkSelfPermission;


/**
 * Fragment that handles the weather operations, along with getting location info from the GPS to
 * access the relevant information
 *
 * @author Amul Pokharel
 */

public class WeatherFragment extends Fragment implements LocationListener {

    private WeatherObj weather;
    private String icon = "";
    private Temperature currentTemp;
    private char currentUnit = 'c';
    private static Handler handler = new Handler();
    private final int LOCATION_GRANTED = 2;
    private boolean permission_checked;
    private long last_checked;
    private final long CHECK_INTERVAL = 60000;
    private Location loc;
    private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;



    @BindView(R.id.weatherIcon) TextView iconView;
    @BindView(R.id.weatherText) TextView weatherText;
    @BindView(R.id.locationName) TextView locationName;
    @BindView(R.id.humidity) TextView humidity;
    @BindView(R.id.pressure) TextView pressure;
    @BindView(R.id.last_update) TextView last_updated;

    public static WeatherFragment newInstance() {

        Bundle args = new Bundle();
        WeatherFragment fragment = new WeatherFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.weather_fragment, container, false);
        ButterKnife.bind(this, view);
        sharedPref  = getActivity().getPreferences(Context.MODE_PRIVATE);
        editor = sharedPref.edit();

        currentUnit = sharedPref.getString("current temp", "c").charAt(0);

        iconView.setTypeface(Typeface.createFromAsset(getActivity().getAssets(), "fonts/weather.ttf"));
        if(savedInstanceState != null){
            permission_checked = savedInstanceState.getBoolean("permission check", false);
            iconView.setText(savedInstanceState.getString("icon"));
            weatherText.setText(savedInstanceState.getString("temperature"));
            humidity.setText(savedInstanceState.getString("humidity"));
            pressure.setText(savedInstanceState.getString("pressure"));
            last_updated.setText(savedInstanceState.getString("last update"));
            locationName.setText(savedInstanceState.getString("location"));
            currentUnit = savedInstanceState.getChar("current unit", 'c');
            currentTemp.setTemp(savedInstanceState.getInt("current temp", 0));
        }
        else{
            permission_checked = false;
            last_checked = 0;
            currentTemp = new Temperature();
        }
        handler.postDelayed(weatherUpdateThread, 0L);
        return view;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void onSaveInstanceState (Bundle outState){
        outState.putBoolean("permission check", permission_checked);
        outState.putLong("last checked", last_checked);
        outState.putString("icon", iconView.getText().toString());
        outState.putString("temperature", weatherText.getText().toString());
        outState.putString("humidity", humidity.getText().toString());
        outState.putString("pressure", pressure.getText().toString());
        outState.putString("last update", last_updated.getText().toString());
        outState.putString("location", locationName.getText().toString());
        outState.putInt("current temp", currentTemp.inCelsius());
        outState.putChar("current unit", currentUnit);

        super.onSaveInstanceState(outState);
    }

    public void onPause(){
        super.onPause();
        editor.putString("current temp", Character.toString(currentUnit));
        editor.commit();
    }


    /**
     * Runnable thread for updating the weather + acquiring location
     */
    private Runnable weatherUpdateThread = new Runnable() {
        @Override
        public void run() {
            if((last_checked == 0) || ((System.currentTimeMillis() - last_checked) >= CHECK_INTERVAL )) {
                last_checked = System.currentTimeMillis();
                updateLocation();
            }
            handler.removeCallbacks(weatherUpdateThread);
        }
    };


    /**
     * Updates the location after getting the location information.
     */
    public void updateLocation(){
        if ((!permission_checked) || ((checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED))){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_GRANTED);
        }
        else{
            LocationManager locationManager = (LocationManager)getActivity().getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            downloadWeather(loc.getLatitude(), loc.getLongitude());
        }
    }



    /**
     * Method that downloads the weather based on the passed parameters
     * @param lat Latitude for acquiring weather
     * @param lng Longitude for acquiring weather
     */
    private void downloadWeather(double lat, double lng){
        String urlText = "http://api.openweathermap.org/data/2.5/weather?lat=" + Double.toString(lat)
                + "&lon=" + Double.toString(lng) + "&appid=50aaa0b9c38198d17df8b2140f09879e&units=metric";

        Log.i("URL", urlText);

        DownloadTask dt = new DownloadTask();
        String result ="";

        try {
            result = dt.execute(urlText).get();
            parseAndupdateUI(result);

        }
        catch (Exception e)
        {
            Log.e("stuff happened", "stuff!");
            e.printStackTrace();
        }
    }

    @OnClick(R.id.weatherText)
    public void convertTemp(){
        String tempString = "";
        if(currentUnit == 'c'){
            tempString = currentTemp.inFahrenheit() + " °F";
            currentUnit = 'f';
        }
        else if(currentUnit == 'f'){
            tempString = currentTemp.inKelvin() +  " °K";
            currentUnit = 'k';
        }
        else{
            tempString = currentTemp.inCelsius() + " °C";
            currentUnit = 'c';
        }

        weatherText.setText(tempString);
    }

    /**
     * Parses the result from downloadWeather into revelant fields and updates the UI
     * @param jsonResult The json result returend by the query from downloadWeather
     * @see public void downloadWeather(double late, double lng)
     */
    private void parseAndupdateUI(String jsonResult){
        Gson gson = new Gson();
        weather = gson.fromJson(jsonResult, WeatherObj.class);
        currentTemp = new Temperature(weather.getMain().getTemp());
        String tempString = "";

        if(currentUnit == 'c')
            tempString = currentTemp.inCelsius() + " °C";
        else if(currentUnit == 'f')
            tempString = currentTemp.inFahrenheit() + " °F";
        else
            tempString = currentTemp.inKelvin() + " °K";
        weatherText.setText(tempString);
        humidity.setText("Humidity: " + (weather.getMain().getHumidity()) +" %");
        pressure.setText("Pressure: " + (weather.getMain().getPressure()) + " hpa");
        last_updated.setText("Last Updated: " + new SimpleDateFormat(("K:mm a, z")).format(new Date(last_checked)));
        locationName.setText(weather.getName());

        icon = "w" + weather.getWeather().get(0).getIcon();
        int resourceID = getActivity().getApplicationContext().getResources().getIdentifier(icon, "string", getActivity().getPackageName());

        if(resourceID != 0) {
            iconView.setText(getString(resourceID));
        }
        else{
            iconView.setText("?");
            //Log.i("Info", icon);
            //Log.i("Info", Integer.toString(resourceID));
        }
    }

    @Override
    /**
     * Function that runs when permission is requested to re-run the needed operations
     * instead of aborting
     */
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case LOCATION_GRANTED: {
                permission_checked = true;
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("yay", "permission granted");
                    updateLocation();

                } else {
                    downloadWeather(27.717245, 85.323960);
                }
                return;
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("debug", "locatiion failed?");
        //downloadWeather( location.getLatitude(), location.getLongitude());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
