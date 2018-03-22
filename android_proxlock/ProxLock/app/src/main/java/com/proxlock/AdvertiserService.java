package com.proxlock;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidParameterSpecException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manages BLE Advertising independent of the main app.
 * If the app goes off screen (or gets killed completely) advertising can continue because this
 * Service is maintaining the necessary Callback in memory.
 */
public class AdvertiserService extends Service {

    private static final String TAG = AdvertiserService.class.getSimpleName();
    private static String aesKey;

    /**
     * A global variable to let AdvertiserFragment check if the Service is running without needing
     * to start or bind to it.
     * This is the best practice method as defined here:
     * https://groups.google.com/forum/#!topic/android-developers/jEvXMWgbgzE
     */
    public static boolean running = false;

    public static final String ADVERTISING_FAILED =
            "com.example.android.bluetoothadvertisements.advertising_failed";

    public static final String ADVERTISING_FAILED_EXTRA_CODE = "failureCode";

    public static final int ADVERTISING_TIMED_OUT = 6;

    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    private AdvertiseCallback mAdvertiseCallback;

    private Handler timeoutHandler;

    private Runnable timeoutRunnable;

    private Handler updateHandler;

    private Runnable updateRunnable;

    private AdvertiseData advertisementData;

    private AdvertiseSettings advertisementSettings;
    private PowerManager pm;
    private PowerManager.WakeLock wl;

    private SharedPreferences prefs;
    /**
     * Length of time to allow advertising before automatically shutting off. (10 minutes)
     */
    private long TIMEOUT = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);

    @Override
    public void onCreate() {
        prefs = getSharedPreferences("com.proxlock.prefs", Context.MODE_PRIVATE);
        aesKey = prefs.getString("aesKey", "");
        Toast.makeText(this, aesKey, Toast.LENGTH_LONG).show();
        //getting a partial wake lock to allow timer to continue to run after user shuts off screen, this is terrible but i don't think there is any other way
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");

        updateHandler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
                advertisementData = buildAdvertiseData();
                mBluetoothLeAdvertiser.startAdvertising(advertisementSettings, advertisementData,
                        mAdvertiseCallback);
                updateHandler.postDelayed(updateRunnable, 60000);
            }
        };

        running = true;
        initialize();
        startAdvertising();
        //setTimeout();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        /**
         * Note that onDestroy is not guaranteed to be called quickly or at all. Services exist at
         * the whim of the system, and onDestroy can be delayed or skipped entirely if memory need
         * is critical.
         */
        running = false;
        stopAdvertising();
        //timeoutHandler.removeCallbacks(timeoutRunnable);
        super.onDestroy();
    }

    /**
     * Required for extending service, but this will be a Started Service only, so no need for
     * binding.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Get references to system Bluetooth objects if we don't have them already.
     */
    private void initialize() {
        if (mBluetoothLeAdvertiser == null) {
            BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager != null) {
                BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
                if (mBluetoothAdapter != null) {
                    mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
                } else {
                    Toast.makeText(this, getString(R.string.bt_null), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.bt_null), Toast.LENGTH_LONG).show();
            }
        }

    }

    /**
     * Starts a delayed Runnable that will cause the BLE Advertising to timeout and stop after a
     * set amount of time.
     */
    private void setTimeout(){
        timeoutHandler = new Handler();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "AdvertiserService has reached timeout of "+TIMEOUT+" milliseconds, stopping advertising.");
                sendFailureIntent(ADVERTISING_TIMED_OUT);
                stopSelf();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT);
    }

    /**
     * Starts BLE Advertising.
     */
    private void startAdvertising() {
        Log.d(TAG, "Service: Starting Advertising");

        if (mAdvertiseCallback == null) {
            advertisementSettings = buildAdvertiseSettings();
            advertisementData = buildAdvertiseData();
            mAdvertiseCallback = new SampleAdvertiseCallback();

            if (mBluetoothLeAdvertiser != null) {
                mBluetoothLeAdvertiser.startAdvertising(advertisementSettings, advertisementData,
                        mAdvertiseCallback);

                updateHandler.postDelayed(updateRunnable, 60000);
                try {
                    wl.acquire();
                }
                catch (Exception e){
                    Writer writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    String s = writer.toString();
                   // Toast.makeText(this, s, Toast.LENGTH_LONG).show();
                }
            }
        }



    }

    /**
     * Stops BLE Advertising.
     */
    private void stopAdvertising() {
        wl.release();
        Log.d(TAG, "Service: Stopping Advertising");
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            mAdvertiseCallback = null;

        }
        updateHandler.removeCallbacks(updateRunnable);
    }

    /**
     * Returns an AdvertiseData object which includes the Service UUID and Device Name.
     */
    private AdvertiseData buildAdvertiseData() {

        /**
         * Note: There is a strict limit of 31 Bytes on packets sent over BLE Advertisements.
         *  This includes everything put into AdvertiseData including UUIDs, device info, &
         *  arbitrary service or manufacturer data.
         *  Attempting to send packets over this limit will result in a failure with error code
         *  AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE. Catch this error in the
         *  onStartFailure() method of an AdvertiseCallback implementation.
         */

        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        //this uuid allows compression to 16 bit service uuid, necessary to fit 16 byte block of time encryption, would be nicer to have a custom uuid, hopefully android extends ability to manually set 16 bit uuids at some point
        ParcelUuid puuid = new ParcelUuid(UUID.fromString("0000FFFE-0000-1000-8000-00805F9B34FB"));
        //ParcelUuid puuid = new ParcelUuid( UUID.fromString( getString( R.string.ble_uuid ) ) );
        dataBuilder.addServiceUuid(puuid);
        //device name won't fit
        //dataBuilder.setIncludeDeviceName(true);

        //this is all a waste of energy and makes life harder anyway, use seconds from epoch
        /*
        Calendar c = Calendar.getInstance();
        Date dateTime = c.getTime();
        DateFormat date = new SimpleDateFormat("MM-dd HH:mm:ss");
        String time = date.format(dateTime);
        */
        //Toast.makeText(this, time, Toast.LENGTH_LONG).show();
        //dataBuilder.addServiceData( puuid, time.getBytes( Charset.forName( "UTF-8" ) ) );

        try {
            //Toast.makeText(this, new String(encryptMsg(time), "UTF-8"), Toast.LENGTH_LONG).show();
            //encryptMsg(time);
            //dataBuilder.addServiceData( puuid, time.getBytes());
            long secondsFromEpoch = System.currentTimeMillis() / 1000;
            Toast.makeText(this, String.valueOf(secondsFromEpoch), Toast.LENGTH_LONG).show();
            //dataBuilder.addServiceData(puuid, longToBytes(secondsFromEpoch));
            //encryptMsg(secondsFromEpoch);
            dataBuilder.addServiceData( puuid, encryptMsg(secondsFromEpoch));

        } catch (NoSuchAlgorithmException e) {
            Toast.makeText(this, "error1", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            Toast.makeText(this, "error2", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            Toast.makeText(this, "error3", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (InvalidParameterSpecException e) {
            Toast.makeText(this, "error4", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            Toast.makeText(this, "error5", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (BadPaddingException e) {
            Toast.makeText(this, "error6", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(this, "error7", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }


        return dataBuilder.build();
    }

    /**
     * Returns an AdvertiseSettings object set to use low power (to help preserve battery life)
     * and disable the built-in timeout since this code uses its own timeout runnable.
     */
    private AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        //high is better for demo, power use difference between medium and high is functionally negligible considering service acquires a partial wake lock
        int txPowerLevel = prefs.getInt("TXSetting", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        settingsBuilder.setTxPowerLevel(txPowerLevel);
        //since i'm updating every minute anyway should i just set timeout to 60000? it shouldn't change anything i don't think
        settingsBuilder.setTimeout(0);
        return settingsBuilder.build();
    }

    /**
     * Custom callback after Advertising succeeds or fails to start. Broadcasts the error code
     * in an Intent to be picked up by AdvertiserFragment and stops this Service.
     */
    private class SampleAdvertiseCallback extends AdvertiseCallback {

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);

            Log.d(TAG, "Advertising failed");
            sendFailureIntent(errorCode);
            stopSelf();

        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertising successfully started");
        }
    }

    /**
     * Builds and sends a broadcast intent indicating Advertising has failed. Includes the error
     * code as an extra. This is intended to be picked up by the {@code AdvertiserFragment}.
     */
    private void sendFailureIntent(int errorCode){
        Intent failureIntent = new Intent();
        failureIntent.setAction(ADVERTISING_FAILED);
        failureIntent.putExtra(ADVERTISING_FAILED_EXTRA_CODE, errorCode);
        sendBroadcast(failureIntent);
    }
//string version
    /*
    public static byte[] encryptMsg(String message)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidParameterSpecException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException
    {
   // Encrypt the message.
        Cipher cipher = null;
        cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        int len = aesKey.length();
        byte[] keyBytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            keyBytes[i / 2] = (byte) ((Character.digit(aesKey.charAt(i), 16) << 4)
                    + Character.digit(aesKey.charAt(i+1), 16));
        }
        SecretKeySpec skeySpec = new SecretKeySpec(keyBytes, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] cipherText = cipher.doFinal(message.getBytes("UTF-8"));
        return cipherText;
    }*/


    public static byte[] encryptMsg(long message)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidParameterSpecException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException
    {
   /* Encrypt the message. */
        Cipher cipher = null;
        cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        int len = aesKey.length();
        byte[] keyBytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            keyBytes[i / 2] = (byte) ((Character.digit(aesKey.charAt(i), 16) << 4)
                    + Character.digit(aesKey.charAt(i+1), 16));
        }
        SecretKeySpec skeySpec = new SecretKeySpec(keyBytes, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] cipherText = cipher.doFinal(longToBytes(message));
        return cipherText;
    }

    public static byte[] longToBytes(long x) {
        //Long.Bytes significantly increases required API level, is functionally equivalent to hard coded 8, hard coding actually prevents more potential future faults than using long.bytes anyway.
        //ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(x);
        return buffer.array();
    }
}