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
        private Controller[] _controllers = new Controller[]
        {
            new Controller(UserIndex.One),
            new Controller(UserIndex.Two),
            new Controller(UserIndex.Three),
            new Controller(UserIndex.Four)
        };

        private bool _isL2Pressed = false;
        private bool _wasL2Pressed = false;
        private bool _isToggleActive = false;
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

        private void UpdateLiveStats(float yaw, float pitch, int dx, int dy, bool isToggleMode, bool isMovementAllowed)
        {
            if ((DateTime.Now - _lastUiUpdate).TotalMilliseconds > 50)
            {
                _lastUiUpdate = DateTime.Now;
                string l2Status;

                if (!_isAnyControllerConnected)
                {
                    l2Status = "N/A (No Controller - Always Active)";
                }
                else if (isToggleMode)
                {
                    l2Status = _isToggleActive ? "TOGGLED ON" : "TOGGLED OFF";
                    l2Status += $" (L2 Pressed: {_isL2Pressed})";
                }
                else
                {
                    l2Status = _isL2Pressed ? "HELD (Active)" : "RELEASED (Ignored)";
                }

                DispatcherQueue.TryEnqueue(() =>
                {
                    LiveStatsBox.Text = $"Mode: {(isToggleMode ? "Toggle" : "Hold")}\nController: {l2Status}\nYaw:   {yaw,8:F4} | Pitch: {pitch,8:F4}\nMouse: dx={dx,4} | dy={dy,4}";
                });
            }
        }
        [DllImport("user32.dll")]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        [StructLayout(LayoutKind.Sequential)]
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
                        if (state.Gamepad.LeftTrigger > 30)
                        {
                            anyL2 = true;
                            break;
                        }
                    }
                }

                // Edge detection for Toggle Mode (only triggers once per pull)
                if (anyL2 && !_wasL2Pressed)
                {
                    _isToggleActive = !_isToggleActive;
                }

                _wasL2Pressed = anyL2;
                _isL2Pressed = anyL2;
                _isAnyControllerConnected = anyConnected;

                await Task.Delay(20);
            }
        }

        private void HandlePacket(byte[] data)
        {
            // Now requires 16 bytes (v2 Protocol)
            if (data.Length < 16) return;

            float yawDelta = BitConverter.ToSingle(data, 0);
            float pitchDelta = BitConverter.ToSingle(data, 4);
            float sensitivity = BitConverter.ToSingle(data, 8);
            bool isToggleMode = BitConverter.ToSingle(data, 12) > 0.5f;

            int dx = (int)(yawDelta * sensitivity);
            int dy = (int)(pitchDelta * sensitivity);

            // Determine if mouse is allowed to move right now
            bool isMovementAllowed = false;
            if (!_isAnyControllerConnected)
            {
                isMovementAllowed = true;
            }
            else if (isToggleMode)
            {
                isMovementAllowed = _isToggleActive;
            }
            else
            {
                isMovementAllowed = _isL2Pressed;
            }

            UpdateLiveStats(yawDelta, pitchDelta, dx, dy, isToggleMode, isMovementAllowed);

            if (!isMovementAllowed) return;

            if (dx != 0 || dy != 0)
            {
                MoveMouse(dx, dy);
            }
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