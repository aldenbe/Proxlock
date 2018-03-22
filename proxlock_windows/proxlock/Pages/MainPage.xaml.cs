using System;
using System.Windows;
using System.Windows.Controls;
using System.Runtime.InteropServices;
using Microsoft.Win32;
using System.Timers;

namespace proxLock.Pages
{
    /// <summary>
    /// Interaction logic for MainPage.xaml
    /// </summary>
    public partial class MainPage : UserControl
    {
        [DllImport("user32.dll")]
        public static extern bool LockWorkStation();

        
        SessionSwitchEventHandler sseh;
        private Watcher watcher;
        private static Timer timer;
        //TIMELIMIT in milliseconds
        private static int TIMELIMIT = 10000;
        
        
        public MainPage()
        {
            sseh = new SessionSwitchEventHandler(SystemEvents_SessionSwitch);
            SystemEvents.SessionSwitch += sseh;
            InitializeComponent();
        }



        private void runBox_Unchecked(object sender, RoutedEventArgs e)
        {
            if (timer != null)
            {
                timer.Dispose();
                timer = null;
            }


            if (watcher != null)
                watcher.StopButton_Click();
            watcher = null;


        }

        private void runBox_Checked(object sender, RoutedEventArgs e)
        {
            startTimer();
            if (watcher == null)
                watcher = new proxLock.Watcher();

            watcher.RunButton_Click();


        }

        // unhook static eventhandler when application terminates!
        private static void timeOut(object source, ElapsedEventArgs e)
        {
            LockWorkStation();
        }

        public static void resetTimer()
        {

            timer.Interval = TIMELIMIT;
            //timer.Stop();
            //timer.Start();
            Console.WriteLine("reset");

        }


        

        //Handle event
        void SystemEvents_SessionSwitch(object sender, SessionSwitchEventArgs e)
        {
            if (e.Reason.ToString() == "SessionLock")
            {
                if (timer != null)
                {
                    timer.Dispose();
                    timer = null;
                }
                if (watcher != null)
                    watcher.StopButton_Click();
            }
            else if (e.Reason.ToString() == "SessionUnlock")
            {
                if(runBox.IsChecked.GetValueOrDefault())
                {
                    startTimer();
                    watcher.RunButton_Click();
                }
            }
            Console.WriteLine(e.Reason.ToString());
        }
        void startTimer()
        {
            if (timer == null)
            {
                timer = new System.Timers.Timer();
                timer.Elapsed += new ElapsedEventHandler(timeOut);
            }
            timer.Interval = TIMELIMIT;
            timer.Enabled = true;
        }

        private void settingsButton_Click(object sender, RoutedEventArgs e)
        {
            runBox.IsChecked = false;
            if (timer != null)
            {
                timer.Dispose();
                timer = null;
            }
            if (watcher != null)
                watcher.StopButton_Click();
            watcher = null;
            Switcher.Switch(new SettingsPage());
        }
    }
}
