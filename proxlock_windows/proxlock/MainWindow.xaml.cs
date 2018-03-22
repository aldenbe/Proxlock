using System.Windows;
using System.Windows.Controls;
using System.IO;
using proxLock.Pages;

namespace proxLock
{


    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        
        






        private string settingsFile = @"E:\proxlock.conf";
        public MainWindow()
        {
            InitializeComponent();
            Switcher.pageSwitcher = this;
            if (File.Exists(settingsFile))
            {
                Switcher.Switch(new MainPage());
            }
            else
            {
                Switcher.Switch(new NoValidSettingsPage());
            }
        }






        public void Navigate(UserControl nextPage)
        {
            this.Content = nextPage;
        }
    }

    
}