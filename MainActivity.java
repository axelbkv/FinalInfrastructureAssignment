package com.example.axelkvistad.finalinfrastructureassignment;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView mTextSpeed;
    private TextView mTextTemperature;
    private TextView mTextSpeedRecord;
    private TextView mTextTemperatureRecord;
    private LinearLayout mBackground;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mThermometer;

    private float myTemp = 0;

    private long lastUpdate = 0;
    private float prevTemp = 0;
    private float lastX, lastY, lastZ;
    private static final int SPEED_THRESHOLD = 300;

    private static final float COLOR_TEMP_MAX = 50; // ambient temp where redness maxes out
    private static final float COLOR_TEMP_MIN = -50; // ambient temp where blueness maxes out


    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference rootRef = database.getReference();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextSpeed = (TextView) findViewById(R.id.text_speed);
        mTextTemperature = (TextView) findViewById(R.id.text_temperature);
        mTextSpeedRecord = (TextView) findViewById(R.id.text_speed_record);
        mTextTemperatureRecord = (TextView) findViewById(R.id.text_temperature_record);
        mBackground = (LinearLayout) findViewById(R.id.content);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mThermometer = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        mSensorManager.registerListener(this, mThermometer, SensorManager.SENSOR_DELAY_NORMAL);

        Query speedQuery = rootRef.child("accelerometer").orderByValue().limitToLast(1);
        speedQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapShot : dataSnapshot.getChildren()) {
                    mTextSpeedRecord.setText(getResources().getString(R.string.device_speed_record, snapShot.getValue()));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                throw databaseError.toException();
            }
        });

        Query tempQuery = rootRef.child("thermometer").orderByValue().limitToLast(1);
        tempQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapShot : dataSnapshot.getChildren()) {
                    mTextTemperatureRecord.setText(getResources().getString(R.string.ambient_temperature_record, snapShot.getValue()));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                throw databaseError.toException();
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mSensor = sensorEvent.sensor;
        long currentTime;
        
        switch(mSensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                currentTime = System.currentTimeMillis();
                float x = sensorEvent.values[0];
                float y = sensorEvent.values[1];
                float z = sensorEvent.values[2];

                // Make sure we only log data every 100ms at most
                if ((currentTime - lastUpdate) > 100) {
                    long timeSinceUpdate = (currentTime - lastUpdate);
                    lastUpdate = currentTime;

                    float speed = (Math.abs(x + y + z - lastX - lastY - lastZ) / timeSinceUpdate * 10000);

                    if (speed > SPEED_THRESHOLD) {
                        String speedStr = String.valueOf(Math.round(speed));
                        mTextSpeed.setText(getResources().getString(R.string.device_speed, speedStr));
                        Date date = new Date(currentTime);
                        String dateStr = date.toString();
                        rootRef.child("accelerometer").child(dateStr).setValue(Double.valueOf(speedStr));
                    }

                    lastX = x;
                    lastY = y;
                    lastZ = z;
                }
                break;

            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                currentTime = System.currentTimeMillis();
                float currentTemp = sensorEvent.values[0];
                float diffTemp = Math.abs(currentTemp - prevTemp);
                if (diffTemp >= 0.2) {
                    prevTemp = currentTemp;
                    String tempStr = String.valueOf(currentTemp);
                    Date date = new Date(currentTime);
                    String dateStr = date.toString();
                    rootRef.child("thermometer").child(dateStr).setValue(Double.valueOf(tempStr));
                    mTextTemperature.setText(getResources().getString(R.string.ambient_temperature, tempStr));
                    float colorTemp = Math.min(currentTemp, COLOR_TEMP_MAX);
                    float tempMaxMinDiff = (Math.abs(COLOR_TEMP_MAX) + Math.abs(COLOR_TEMP_MIN)) / 2;
                    colorTemp = (Math.max(colorTemp, COLOR_TEMP_MIN) + tempMaxMinDiff) / 100; // [0..1]
                    Log.d("debug", "colorTemp: " + colorTemp);
                    int colorInt = Color.rgb(colorTemp, 0f, 1f - colorTemp);
                    mBackground.setBackgroundColor(colorInt);

                }
                break;

            default:

                break;
        }

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /** Unregister the sensor when the application hibernates */
    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    /** Register the sensor again when the application resumes */
    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }



}
