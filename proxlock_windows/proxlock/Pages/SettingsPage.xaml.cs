using System;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;

namespace proxLock.Pages
{
    /// <summary>
    /// Interaction logic for SettingsPage.xaml
    /// </summary>
    public partial class SettingsPage : UserControl
    {
        private static string settingsFilePath = @"E:\proxlock.conf";
        TextWriter settingsWriter;
        private string aesKey;
        private int rssi;
        private string password;
        private int oldRange;
        //fixme currently assumed txPower, may vary -40 is a safe bet on android devices
        int txPower = -40;
        //n is propagation constant or path-loss exponent, 2 in free space assume free space
        //should be higher if user wants computer to unlock through walls, assume user doesn't want to unlock computers in rooms user is not in.
        int n = 2;
        public SettingsPage()
        {
            InitializeComponent();
            string[] settings = File.ReadAllLines(settingsFilePath);
            foreach (var line in settings)
            {
                int equalsIndex = line.IndexOf('=');
                switch (line.Substring(0, equalsIndex))
                {
                    case "Password":
                        password = line.Substring(equalsIndex + 1);
                        break;
                    case "RSSI":
                        rssi = Convert.ToInt32(line.Substring(equalsIndex + 1));
                        break;
                    case "Key":
                        aesKey = line.Substring(equalsIndex + 1);
                        break;

                }
            }
            oldRange = Convert.ToInt32(Math.Pow(Math.E, -((rssi - txPower) / (10.0 * n))));
            rangeBox.Text = Convert.ToString(oldRange);
        }

        private void backButton_Click(object sender, RoutedEventArgs e)
        {
            Switcher.Switch(new MainPage());
        }

        private void submitButton_Click(object sender, RoutedEventArgs e)
        {
            
            Regex intTest = new Regex("^[0-9]+$");
            if (intTest.IsMatch(rangeBox.Text))
            {
                int newRange = Convert.ToInt32(rangeBox.Text);
                if ( newRange > 0)
                {
                    //distance in feet 10 assumed default
                    int RSSI = txPower - Convert.ToInt32(10 * n * Math.Log(newRange));
                    File.WriteAllText(settingsFilePath, "");
                    settingsWriter = new StreamWriter(settingsFilePath);
                    settingsWriter.WriteLine("Key=" + aesKey);
                    settingsWriter.WriteLine("Password=" + password);
                    settingsWriter.WriteLine("RSSI=" + RSSI);
                    settingsWriter.Close();

                    Switcher.Switch(new MainPage());
                }
            }
           

        }
    }
}
