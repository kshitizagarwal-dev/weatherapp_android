package com.example.weather2

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.weather2.models.WeatherResponse
import com.example.weather2.networks.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*



class MainActivity : AppCompatActivity() {


    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var myProgressDialog: Dialog? = null
    private  lateinit var mSharedPrefrences : SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)



       mSharedPrefrences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        setupUI()
        if (!isLocationEnabled()) {

            Toast.makeText(this,
                    "Your location Provider is turned off. Please turned on",
                    Toast.LENGTH_SHORT).show()

            //Redirect to the settings
            // using intent

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this).withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {

                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {

                        requestLocationData()
                    }
                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(this@MainActivity,
                                "Your have denied location permission. " +
                                        "Please all.Please enable them as it is mandatory for app to work",
                                Toast.LENGTH_SHORT).show()

                    }
                }

                override fun onPermissionRationaleShouldBeShown(report: MutableList<PermissionRequest>?,
                                                                token: PermissionToken?) {
                    showRationalDialogForPermission()
                }


            }).onSameThread()
                    .check()

        }

    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")

            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {

            val retrofit: Retrofit = Retrofit.Builder()
                    .baseUrl(Constants.BASE_URL)// using the api url to call
                    //converting the api data into the Gson or in the right format
                    .addConverterFactory(GsonConverterFactory.create())
                    .build() // building the retrofit call

            val service: WeatherService = retrofit
                    .create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(
                    latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()


            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Erorrrrrrr....", t.message.toString())
                    hideCustomProgressDialog()
                }

                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    if (response.isSuccessful) {

                        hideCustomProgressDialog()


                        val weatherList: WeatherResponse? = response.body()
                        if (weatherList != null) {

                            val weatherResponseJsonString = Gson().toJson(weatherList)
                            val editor = mSharedPrefrences.edit()
                            editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                            editor.apply()

                            setupUI()
                        }


                    } else {
                        when (response.code()) {

                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }

                        }
                    }
                }

            })


        } else {

            Toast.makeText(this@MainActivity,
                    "No internet connection available",
                    Toast.LENGTH_SHORT
            ).show()
        }

    }

    private fun showRationalDialogForPermission() {
        AlertDialog.Builder(this)
                .setMessage("It looks like you have turned off the permissions required for this feature.It can be enabled under Application Setting")
                .setPositiveButton(
                        "GO TO SETTINGS"
                ) { _, _ ->
                    try {

                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)

                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel") { dialog,
                                               _ ->
                    dialog.dismiss()
                }.show()

    }

    private fun isLocationEnabled(): Boolean {

        // this provides access to the system location services
        val locationManager: LocationManager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    // To Show the custom progress Dialog
    private fun showCustomProgressDialog() {

        myProgressDialog = Dialog(this)
        /* Set the screen content from a layout resource.
        The resource will be  inflated ,a dding all top level views to the screen
         */

        myProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        myProgressDialog!!.show()
    }

    private fun hideCustomProgressDialog() {

        if (myProgressDialog != null) {
            myProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    private fun setupUI() {

        val weatherResponseJsonSting = mSharedPrefrences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if(!weatherResponseJsonSting.isNullOrEmpty())
        {
            val weatherList  = Gson().fromJson(weatherResponseJsonSting, WeatherResponse::class.java)

             for (i in weatherList.weather.indices) {
             Log.i("Name Weather", weatherList.weather.toString())
            tv_main.text = weatherList.weather[i].main
            tv_main_description.text = weatherList.weather[i].description
            tv_temp.text = weatherList.main.temp.toString() + 
                                 getUnit(application.resources.configuration.toString())

            tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
            tv_sunset_time.text = unixTime(weatherList.sys.sunset)
            tv_min.text = weatherList.main.temp_min.toString() + "min"
            tv_max.text = weatherList.main.temp_max.toString() + "max"
            tv_speed.text = weatherList.wind.speed.toString()

            tv_humidity.text = weatherList.main.humidity.toString() + "per cent"
            tv_name.text = weatherList.name
            tv_country.text = weatherList.sys.country

            when(weatherList.weather[i].icon){
                "01d" -> iv_main.setImageResource(R.drawable.sunny)
                "02d" -> iv_main.setImageResource(R.drawable.cloud)
                "03d" -> iv_main.setImageResource(R.drawable.cloud)
                "04d" -> iv_main.setImageResource(R.drawable.cloud)
                "04n" -> iv_main.setImageResource(R.drawable.cloud)
                "10d" -> iv_main.setImageResource(R.drawable.rain)
                "11d" -> iv_main.setImageResource(R.drawable.storm)
                "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                "01n" -> iv_main.setImageResource(R.drawable.cloud)
                "02n" -> iv_main.setImageResource(R.drawable.cloud)
                "03n" -> iv_main.setImageResource(R.drawable.cloud)
                "10n" -> iv_main.setImageResource(R.drawable.cloud)
                "11n" -> iv_main.setImageResource(R.drawable.rain)
                "13n" -> iv_main.setImageResource(R.drawable.snowflake)
            }}

        }

    }


    private fun getUnit(value: String): String? {

        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {

            value = "°F"
        }
        return value
    }


    private fun unixTime(timex : Long): String?{

        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}