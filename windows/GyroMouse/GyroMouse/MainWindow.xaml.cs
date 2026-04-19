using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using System;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using SharpDX.XInput;

namespace GyroMouse
{
    public sealed partial class MainWindow : Window
    {
        // Check ALL 4 possible Xbox controller slots (fixes the Moonlight "Player 2" bug)
        private Controller[] _controllers = new Controller[]
        {
            new Controller(UserIndex.One),
            new Controller(UserIndex.Two),
            new Controller(UserIndex.Three),
            new Controller(UserIndex.Four)
        };

        private bool _isL2Pressed = false;
        private bool _isAnyControllerConnected = false;
        private DateTime _lastUiUpdate = DateTime.MinValue;

        public MainWindow()
        {
            InitializeComponent();
            Log("App started. Monitoring ALL controller slots.");
            StartControllerMonitor();
            _ = StartUdpListenerAsync();
            Closed += Window_Closed;
        }

        private void Log(string message)
        {
            DispatcherQueue.TryEnqueue(() =>
            {
                LogBox.Text += $"[{DateTime.Now:HH:mm:ss}] {message}\n";
            });
        }

        private void UpdateLiveStats(float yaw, float pitch, int dx, int dy)
        {
            if ((DateTime.Now - _lastUiUpdate).TotalMilliseconds > 50)
            {
                _lastUiUpdate = DateTime.Now;
                string l2Status = _isAnyControllerConnected ? (_isL2Pressed ? "PRESSED (Active)" : "RELEASED (Ignored)") : "N/A (No Controller)";

                DispatcherQueue.TryEnqueue(() =>
                {
                    LiveStatsBox.Text = $"Controller: {l2Status}\nYaw:   {yaw,8:F4} | Pitch: {pitch,8:F4}\nMouse: dx={dx,4} | dy={dy,4}";
                });
            }
        }

        [DllImport("user32.dll")]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize); [StructLayout(LayoutKind.Sequential)]
        private struct INPUT { public uint type; public MOUSEINPUT mi; }
        [StructLayout(LayoutKind.Sequential)]
        private struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public nint dwExtraInfo; }

        private const uint INPUT_MOUSE = 0;
        private const uint MOUSEEVENTF_MOVE = 0x0001;

        private void MoveMouse(int dx, int dy)
        {
            var input = new INPUT { type = INPUT_MOUSE, mi = new MOUSEINPUT { dx = dx, dy = dy, dwFlags = MOUSEEVENTF_MOVE } };
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
                    HandlePacket(result.Buffer);
                }
                catch (ObjectDisposedException) { Log("UDP listener stopped."); break; }
                catch (Exception ex) { Log($"UDP error: {ex.Message}"); break; }
            }
        }

        private async void StartControllerMonitor()
        {
            while (true)
            {
                bool anyConnected = false;
                bool anyL2 = false;

                foreach (var ctrl in _controllers)
                {
                    if (ctrl.IsConnected)
                    {
                        anyConnected = true;
                        var state = ctrl.GetState();
                        // 30 is the threshold out of 255 to prevent accidental light touches
                        if (state.Gamepad.LeftTrigger > 30)
                        {
                            anyL2 = true;
                            break;
                        }
                    }
                }

                _isAnyControllerConnected = anyConnected;
                _isL2Pressed = anyL2;

                await Task.Delay(20);
            }
        }

        private float _sensitivity = 500f;

        private void HandlePacket(byte[] data)
        {
            if (data.Length < 8) return;

            float yawDelta = BitConverter.ToSingle(data, 0);
            float pitchDelta = BitConverter.ToSingle(data, 4);

            int dx = (int)(yawDelta * _sensitivity);
            int dy = (int)(pitchDelta * _sensitivity);

            // Send to Live Stats UI even if L2 isn't pressed (helps with debugging)
            UpdateLiveStats(yawDelta, pitchDelta, dx, dy);

            // Only move mouse if L2 is held (OR if no controller is detected at all)
            if (_isAnyControllerConnected && !_isL2Pressed) return;

            if (dx != 0 || dy != 0)
            {
                MoveMouse(dx, dy);
            }
        }

        private void SensSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
        {
            _sensitivity = (float)e.NewValue;
            if (SensLabel != null) SensLabel.Text = ((int)_sensitivity).ToString();
        }

        private void Window_Closed(object sender, WindowEventArgs args)
        {
            try
            {
                _udp?.Close();
                _udp?.Dispose();
            }
            catch { }
        }
    }
}