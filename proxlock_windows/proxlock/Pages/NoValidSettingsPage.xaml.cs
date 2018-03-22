using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;

namespace proxLock.Pages
{
    /// <summary>
    /// Interaction logic for NoValidSettingsPage.xaml
    /// </summary>
    public partial class NoValidSettingsPage : UserControl
    {

        RfcommServiceProvider rfcommProvider;
        StreamSocketListener socketListener;
        private StreamSocket socket;
        private static string settingsFilePath = @"E:\proxlock.conf";
        TextWriter settingsWriter;


        public NoValidSettingsPage()
        {
            InitializeComponent();
            try
            {
                File.WriteAllText(settingsFilePath, "");
            } catch
            {
                //an empty catch generally looks terrible but here it's actually fine, the only possible error is file doesn't exist, which is what i want
            }
            
            
            settingsWriter = new StreamWriter(settingsFilePath);

            
        }

        private async void connectButton_Click(object sender, RoutedEventArgs e)
        {
            if (String.IsNullOrEmpty(passwordBox.Password))
            {
                //notify user?
                Console.WriteLine("nopassword");
                return;
            }
            Guid applicationUuid = Guid.Parse("B4ED3F14-EBCE-46BE-A1EA-DFF6BF16A8D8");
            try
            {

                rfcommProvider = await RfcommServiceProvider.CreateAsync(RfcommServiceId.FromUuid(applicationUuid));
            }
            // Catch exception HRESULT_FROM_WIN32(ERROR_DEVICE_NOT_AVAILABLE).
            catch (Exception ex) when ((uint)ex.HResult == 0x800710DF)
            {
                return;
            }


            // Create a listener for this service and start listening
            socketListener = new StreamSocketListener();
            socketListener.ConnectionReceived += OnConnectionReceived;
            var rfcomm = rfcommProvider.ServiceId.AsString();

            await socketListener.BindServiceNameAsync(rfcommProvider.ServiceId.AsString(),
                SocketProtectionLevel.BluetoothEncryptionAllowNullAuthentication);

            // Set the SDP attributes and start Bluetooth advertising
            //InitializeServiceSdpAttributes(rfcommProvider);

            try
            {
                rfcommProvider.StartAdvertising(socketListener, true);
            }
            catch (Exception ee)
            {
                Console.WriteLine(ee);
                return;
            }


        }
        async private void OnConnectionReceived(StreamSocketListener sender, StreamSocketListenerConnectionReceivedEventArgs args)
        {
            socket = args.Socket;
            var remoteDevice = await BluetoothDevice.FromHostNameAsync(socket.Information.RemoteHostName);
            Console.WriteLine("connection recieved");
            while (true)
            {
                try
                {
                    var reader = new DataReader(socket.InputStream);
                    var writer = new DataWriter(socket.OutputStream);
                    // Based on the protocol we've defined, the first uint is the size of the message
                    uint readLength = await reader.LoadAsync(sizeof(uint));

                    // Check if the size of the data is expected (otherwise the remote has already terminated the connection)
                    if (readLength < sizeof(uint))
                    {
                        //disconnected early
                        break;
                    }
                    uint currentLength = reader.ReadUInt32();

                    // Load the rest of the message since you already know the length of the data expected.  
                    readLength = await reader.LoadAsync(currentLength);

                    
                    string message = reader.ReadString(currentLength);
                    Console.WriteLine(message);
                    writer.WriteString(message);
                    await writer.StoreAsync();
                    Console.WriteLine("written");
                    await reader.LoadAsync(sizeof(uint));
                    int response = reader.ReadInt32();
                    Console.WriteLine("after await");
                    if (response == 1)
                    {
                        //success
                        write_first_time_settings(message);
                        break;
                    }
                    else
                    {
                        //failure, app will try again, go through loop again
                        Console.WriteLine("error");
                        continue;

                    }

                }
                // Catch exception HRESULT_FROM_WIN32(ERROR_OPERATION_ABORTED).
                catch (Exception ex) when ((uint)ex.HResult == 0x800703E3)
                {

                    //await Dispatcher.RunAsync(Windows.UI.Core.CoreDispatcherPriority.Normal, () =>
                    //{
                    //    rootPage.NotifyUser("Client Disconnected Successfully", NotifyType.StatusMessage);
                    //});
                    //break;
                }
            }

            
            //if (remoteDisconnection)
            //{
            //    Disconnect();
            //    await Dispatcher.RunAsync(Windows.UI.Core.CoreDispatcherPriority.Normal, () =>
            //    {
            //        rootPage.NotifyUser("Client disconnected", NotifyType.StatusMessage);
            //    });
            //}
        }
        public void write_first_time_settings(string key)
        {
            string password = encrypt_password();
            //fixme currently assumed txPower, may vary -40 is a safe bet on android devices
            int txPower = -40;
            //n is propagation constant or path-loss exponent, 2 in free space assume free space
            //should be higher if user wants computer to unlock through walls, assume user doesn't want to unlock computers in rooms user is not in.
            int n = 2;
            //distance in feet 10 assumed default
            int distance = 10;
            int RSSI = txPower - Convert.ToInt32(10 * n * Math.Log(distance));
            settingsWriter.WriteLine("Key=" + key);
            settingsWriter.WriteLine("Password=" + password.ToUpper());
            settingsWriter.WriteLine("RSSI=" + RSSI);
            settingsWriter.Close();
            Application.Current.Dispatcher.Invoke((Action)delegate {

                Switcher.Switch(new MainPage());

            });
        }

        private string encrypt_password()
        {
            string password;
            byte[] encrypted;
            string passwordKey = "D4A9C8DC8DB79DD6B0DE415EC71254EB";
            int NumberChars = passwordKey.Length;
            byte[] btkey = new byte[NumberChars / 2];
            for (int i = 0; i < NumberChars; i += 2)
                btkey[i / 2] = Convert.ToByte(passwordKey.Substring(i, 2), 16);
            RijndaelManaged aes128 = new RijndaelManaged();
            aes128.Mode = CipherMode.ECB;
            aes128.Padding = PaddingMode.PKCS7;
            using (RijndaelManaged rijAlg = new RijndaelManaged())
            {
                rijAlg.Key = btkey;
                rijAlg.Mode = CipherMode.ECB;
                ICryptoTransform encryptor = rijAlg.CreateEncryptor(rijAlg.Key, null);

                // Create the streams used for encryption. 
                using (MemoryStream msEncrypt = new MemoryStream())
                {
                    using (CryptoStream csEncrypt = new CryptoStream(msEncrypt, encryptor, CryptoStreamMode.Write))
                    {
                        using (StreamWriter swEncrypt = new StreamWriter(csEncrypt))
                        {

                            //Write all data to the stream.
                            swEncrypt.Write(passwordBox.Password);
                        }
                        encrypted = msEncrypt.ToArray();
                    }
                }

            }

            password = ByteArrayToString(encrypted);
            
            return password;
        }
        public static string ByteArrayToString(byte[] ba)
        {
            StringBuilder hex = new StringBuilder(ba.Length * 2);
            foreach (byte b in ba)
                hex.AppendFormat("{0:x2}", b);
            return hex.ToString();
        }
    }
}
