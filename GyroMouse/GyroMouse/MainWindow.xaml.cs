using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Text;
using System.Threading.Tasks;
using Windows.Foundation;
using Windows.Foundation.Collections;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace GyroMouse
{
    /// <summary>
    /// An empty window that can be used on its own or navigated to within a Frame.
    /// </summary>
    public sealed partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
            Log("App started.");
            _ = StartUdpListenerAsync();
        }

        private void Log(string message)
        {
            // Must run on the UI thread
            DispatcherQueue.TryEnqueue(() =>
            {
                LogBox.Text += $"[{DateTime.Now:HH:mm:ss.fff}] {message}\n";
            });
        }

        [DllImport("user32.dll")]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        [StructLayout(LayoutKind.Sequential)]
        private struct INPUT
        {
            public uint type;
            public MOUSEINPUT mi;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct MOUSEINPUT
        {
            public int dx, dy;
            public uint mouseData, dwFlags, time;
            public nint dwExtraInfo;
        }

        private const uint INPUT_MOUSE = 0;
        private const uint MOUSEEVENTF_MOVE = 0x0001;

        private void MoveMouse(int dx, int dy)
        {
            var input = new INPUT
            {
                type = INPUT_MOUSE,
                mi = new MOUSEINPUT { dx = dx, dy = dy, dwFlags = MOUSEEVENTF_MOVE }
            };
            SendInput(1, [input], Marshal.SizeOf<INPUT>());
        }

        private void TestMouseBtn_Click(object sender, RoutedEventArgs e)
        {
            MoveMouse(50, 50);
            Log("Mouse nudged +50, +50");
        }

        private UdpClient? _udp;

        private async Task StartUdpListenerAsync()
        {
            _udp = new UdpClient(26760);
            Log("UDP listening on port 26760...");

            while (true)
            {
                try
                {
                    var result = await _udp.ReceiveAsync();
                    var text = Encoding.UTF8.GetString(result.Buffer);
                    Log($"UDP from {result.RemoteEndPoint}: {text.Trim()} ({result.Buffer.Length} bytes)");
                }
                catch (Exception ex)
                {
                    Log($"UDP error: {ex.Message}");
                    break;
                }
            }
        }
    }
}
