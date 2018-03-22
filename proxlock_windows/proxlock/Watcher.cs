using System;
using System.Linq;
using Windows.Storage.Streams;
using Windows.Devices.Bluetooth.Advertisement;
using System.Security.Cryptography;
using System.IO;
using proxLock.Pages;

namespace proxLock
{
    class Watcher
    {
        private BluetoothLEAdvertisementWatcher bleWatcher;
        private string aesKey;
        private int rssi;
        private static string settingsFilePath = @"E:\proxlock.conf";

        public Watcher()
        {
            string[] settings = File.ReadAllLines(settingsFilePath);
            foreach(var line in settings)
            {
                int equalsIndex = line.IndexOf('=');
                switch (line.Substring(0, equalsIndex))
                {
                    case "RSSI":
                        rssi = Convert.ToInt32(line.Substring(equalsIndex + 1));
                        break;
                    case "Key":
                        aesKey = line.Substring(equalsIndex + 1);
                        break;

                }
            }
            bleWatcher = new BluetoothLEAdvertisementWatcher();

            bleWatcher.Received += OnAdvertisementReceived;
            bleWatcher.Stopped += OnAdvertisementWatcherStopped;

        }

        private async void OnAdvertisementReceived(BluetoothLEAdvertisementWatcher watcher, BluetoothLEAdvertisementReceivedEventArgs eventArgs)
        {
            //make sure device is in configured range
            if (eventArgs.RawSignalStrengthInDBm > rssi)
            {
                long secondsFromEpoch = (long)(DateTime.UtcNow.Subtract(new DateTime(1970, 1, 1))).TotalSeconds;

                try
                {
                    var advertisementData = new byte[eventArgs.Advertisement.DataSections.ElementAt(2).Data.Length];
                    using (var reader = DataReader.FromBuffer(eventArgs.Advertisement.DataSections.ElementAt(2).Data))
                    {
                        reader.ReadBytes(advertisementData);
                    }


                    int NumberChars = aesKey.Length;
                    byte[] btkey = new byte[NumberChars / 2];
                    for (int i = 0; i < NumberChars; i += 2)
                        btkey[i / 2] = Convert.ToByte(aesKey.Substring(i, 2), 16);

                    RijndaelManaged aes128 = new RijndaelManaged();
                    aes128.Mode = CipherMode.ECB;
                    aes128.Padding = PaddingMode.PKCS7;
                    ICryptoTransform decryptor = aes128.CreateDecryptor(btkey, null);

                    

                    Console.WriteLine(BitConverter.ToString(advertisementData));
                    //first two bytes of data should be ignored
                    MemoryStream ms = new MemoryStream(advertisementData, 2, advertisementData.Length - 2);
                    CryptoStream cs = new CryptoStream(ms, decryptor, CryptoStreamMode.Read);
                    byte[] decryptedData = new byte[advertisementData.Length - 2];



                    int decryptCount = cs.Read(decryptedData, 0, decryptedData.Length);
                    if (BitConverter.IsLittleEndian)
                        Array.Reverse(decryptedData);

                    ms.Close();
                    cs.Close();

                    //return plaintext in String
                    //string timeString = Encoding.UTF8.GetString(decryptedDateTimestamp, 0, decryptCount);

                    long decryptedTimeStamp = BitConverter.ToInt64(decryptedData, 8);
                    long timeDif = (secondsFromEpoch - decryptedTimeStamp);
                    //Console.WriteLine(secondsFromEpoch);
                    //char[] delimiterChars = { '-', ' ', ':' };

                    //string[] timeValues = (timeString.Split(delimiterChars));
                    //DateTime curDateTime = DateTime.Now;
                    //DateTime authDateTimeStamp = new DateTime(curDateTime.Year, Int32.Parse(timeValues[0]), Int32.Parse(timeValues[1]), Int32.Parse(timeValues[2]), Int32.Parse(timeValues[3]), Int32.Parse(timeValues[4]));

                    //Compare current date and time to decrypted auth message
                    // double secondsFromAuthDateTimeStamp = (curDateTime - authDateTimeStamp).TotalSeconds;
                    //Console.WriteLine(authDateTimeStamp);
                    //Console.WriteLine(curDateTime);

                    if (timeDif >= 0 && timeDif < 60)
                        MainPage.resetTimer();
                }
                catch
                {

                }

            }

        }

        /// <summary>
        /// Invoked as an event handler when the watcher is stopped or aborted.
        /// </summary>
        /// <param name="watcher">Instance of watcher that triggered the event.</param>
        /// <param name="eventArgs">Event data containing information about why the watcher stopped or aborted.</param>
        private async void OnAdvertisementWatcherStopped(BluetoothLEAdvertisementWatcher watcher, BluetoothLEAdvertisementWatcherStoppedEventArgs eventArgs)
        {
            /*
            // Notify the user that the watcher was stopped
            await this.Dispatcher.RunAsync(Windows.UI.Core.CoreDispatcherPriority.Normal, () =>
            {
                rootPage.NotifyUser(string.Format("Watcher stopped or aborted: {0}", eventArgs.Error.ToString()), NotifyType.StatusMessage);
            });
            */
        }




        /// <summary>
        /// Invoked as an event handler when the Run button is pressed.
        /// </summary>
        /// <param name="sender">Instance that triggered the event.</param>
        /// <param name="e">Event data describing the conditions that led to the event.</param>
        public void RunButton_Click(/*object sender, RoutedEventArgs e*/)
        {
            // Calling watcher start will start the scanning if not already initiated by another client
            bleWatcher.Start();

        }

        /// <summary>
        /// Invoked as an event handler when the Stop button is pressed.
        /// </summary>
        /// <param name="sender">Instance that triggered the event.</param>
        /// <param name="e">Event data describing the conditions that led to the event.</param>
        public void StopButton_Click(/*object sender, RoutedEventArgs e*/)
        {
            // Stopping the watcher will stop scanning if this is the only client requesting scan
            bleWatcher.Stop();

        }


    }
}
