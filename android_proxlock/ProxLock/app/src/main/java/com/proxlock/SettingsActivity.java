package com.proxlock;

import android.app.ActionBar;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private Spinner powerSpinner;
    private SharedPreferences prefs;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences("com.proxlock.prefs", Context.MODE_PRIVATE);
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        powerSpinner = (Spinner) findViewById(R.id.powerSpinner);
        populatePowerSpinner();
        Button connectNewPCButton = (Button) findViewById(R.id.connectNewPCButton);
        connectNewPCButton.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent newConnectionIntent = new Intent(SettingsActivity.this, NewConnection.class);
                startActivity(newConnectionIntent);
            }
        });
    }

    void populatePowerSpinner(){
        List<String> list = new ArrayList<String>();
        list.add("Ultra Low Power (Not advised)");
        list.add("Low Power");
        list.add("Medium Power");
        list.add("High Power");
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        powerSpinner.setAdapter(dataAdapter);
        int powerSettings = prefs.getInt("powerStrength", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        switch(powerSettings){
            case AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW:
                powerSpinner.setSelection(dataAdapter.getPosition("Ultra Low Power (Not advised)"));
                break;
            case AdvertiseSettings.ADVERTISE_TX_POWER_LOW:
                powerSpinner.setSelection(dataAdapter.getPosition("Low Power"));
                break;
            case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM:
                powerSpinner.setSelection(dataAdapter.getPosition("Medium Power"));
                break;
            case AdvertiseSettings.ADVERTISE_TX_POWER_HIGH:
                powerSpinner.setSelection(dataAdapter.getPosition("High Power"));
                break;
        }
        powerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //initialize to default to stop compiler complaints mostly, but also handles unforseen cases.
                int newPowerSetting = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;
                switch(parent.getItemAtPosition(position).toString()){
                    case "Ultra Low Power (Not advised)":
                        newPowerSetting = AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW;
                        break;
                    case "Low Power":
                        newPowerSetting = AdvertiseSettings.ADVERTISE_TX_POWER_LOW;
                        break;
                    case "Medium Power":
                        newPowerSetting = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
                        break;
                    case "High Power":
                        newPowerSetting = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;
                        break;
                }
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("powerStrength", newPowerSetting);
                editor.commit();

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }
}
