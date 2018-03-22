package com.proxlock;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;


public class NewConnection extends AppCompatActivity {
    private SharedPreferences prefs;
    private String aesKey;
    private UUID applicationUUID = UUID.fromString("B4ED3F14-EBCE-46BE-A1EA-DFF6BF16A8D8");
    private BluetoothSocket mBluetoothSocket;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_new_connection);
        prefs = getSharedPreferences("com.proxlock.prefs", Context.MODE_PRIVATE);
        aesKey = prefs.getString("aesKey", "");
        if(aesKey == "") {
            try {

                KeyGenerator keygen = KeyGenerator.getInstance("AES");
                keygen.init(128);
                SecretKey secretKey = keygen.generateKey();
                byte[] key = secretKey.getEncoded();
                aesKey = bytesToHex(key);

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        } else {
            android.support.v7.app.ActionBar actionBar = getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        final Button connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                connectToPC();




            }
        });
    }

    private void connectToPC(){
        //Toast.makeText(this, aesKey, Toast.LENGTH_LONG).show();
        final BluetoothAdapter myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final AlertDialog.Builder popDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View Viewlayout = inflater.inflate(R.layout.bt_list, (ViewGroup) findViewById(R.id.bt_list));

        popDialog.setTitle("Paired Bluetooth Devices");
        popDialog.setView(Viewlayout);

        // create the arrayAdapter that contains the BTDevices, and set it to a ListView
        ListView myListView = (ListView) Viewlayout.findViewById(R.id.BTList);
        myListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapter,View v, int position, long id){
                String item = (String) adapter.getItemAtPosition(position);
                BluetoothDevice myPC = myBluetoothAdapter.getRemoteDevice(item.substring(item.lastIndexOf('\n')+1));
                try {
                    v.setVisibility(View.GONE);
                    mBluetoothSocket = myPC.createRfcommSocketToServiceRecord(applicationUUID);
                    mBluetoothSocket.connect();
                    InputStream inputStream = mBluetoothSocket.getInputStream();
                    OutputStream outputStream = mBluetoothSocket.getOutputStream();
                    byte[] messageByteArray = aesKey.getBytes();
                    while (true) {
                        outputStream.write(ByteBuffer.allocate(4).putInt(messageByteArray.length).array());
                        outputStream.write(messageByteArray);
                        byte[] buffer = new byte[messageByteArray.length];
                        int numBytes = inputStream.read(buffer);
                        Toast.makeText(NewConnection.this, "1" + aesKey, Toast.LENGTH_LONG).show();
                        String readmessage = new String(buffer, 0, numBytes);
                        Toast.makeText(NewConnection.this, "2" + readmessage, Toast.LENGTH_LONG).show();
                        if (readmessage.equals(aesKey)) {
                            outputStream.write(ByteBuffer.allocate(4).putInt(1).array());
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("aesKey", aesKey);
                            editor.commit();
                            Intent startIntent = new Intent(NewConnection.this, MainActivity.class);
                            NewConnection.this.finish();
                            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            NewConnection.this.startActivity(startIntent);
                        } else {
                            outputStream.write(ByteBuffer.allocate(4).putInt(0).array());
                        }
                    }


                } catch (IOException e) {
                    Toast.makeText(NewConnection.this, "Error occurred, please try again", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }

            }
        });
        ArrayAdapter<String> BTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        myListView.setAdapter(BTArrayAdapter);

        // get paired devices
        Set<BluetoothDevice> pairedDevices = myBluetoothAdapter.getBondedDevices();

        // put it's one to the adapter
        for(BluetoothDevice device : pairedDevices)
            BTArrayAdapter.add(device.getName()+ "\n" + device.getAddress());

        // Button OK
        popDialog.setPositiveButton("Pair",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(NewConnection.this, "To be added later", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }

                }
        );

        // Create popup and show
        popDialog.create();
        popDialog.show();
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    @Override
    public void onBackPressed()
    {
        //let user go back if a key is defined, for add new connection button in settings basically
        if (aesKey != ""){
            finish();
        }
        //stops user from going back to start screen without key set for now, handle this better later
    }

    private void kill_activity(){
        finish();
    }


}
