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

        private bool _isBtnPressed = false;
        private bool _wasBtnPressed = false;
        private bool _isToggleActive = false;
        private bool _isAnyControllerConnected = false;
        private int _activeButton = 0; // 0=L2, 1=R2, 2=L1, 3=R1
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
                string btnName = _activeButton switch { 0 => "L2", 1 => "R2", 2 => "L1", 3 => "R1", _ => "Btn" };
                string btnStatus;

                if (!_isAnyControllerConnected)
                {
                    btnStatus = "N/A (No Controller - Always Active)";
                }
                else if (isToggleMode)
                {
                    btnStatus = _isToggleActive ? "TOGGLED ON" : "TOGGLED OFF";
                    btnStatus += $" ({btnName} Pressed: {_isBtnPressed})";
                }
                else
                {
                    btnStatus = _isBtnPressed ? "HELD (Active)" : "RELEASED (Ignored)";
                }

                DispatcherQueue.TryEnqueue(() =>
                {
                    LiveStatsBox.Text = $"Mode: {(isToggleMode ? "Toggle" : "Hold")} | Target: {btnName}\nController: {btnStatus}\nYaw:   {yaw,8:F4} | Pitch: {pitch,8:F4}\nMouse: dx={dx,4} | dy={dy,4}";
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
                bool anyBtn = false;

                foreach (var ctrl in _controllers)
                {
                    if (ctrl.IsConnected)
                    {
                        anyConnected = true;
                        var state = ctrl.GetState();
                        bool currentBtnState = false;

                        // Check the specific button requested by Android
                        switch (_activeButton)
                        {
                            case 0: currentBtnState = state.Gamepad.LeftTrigger > 30; break;
                            case 1: currentBtnState = state.Gamepad.RightTrigger > 30; break;
                            case 2: currentBtnState = (state.Gamepad.Buttons & GamepadButtonFlags.LeftShoulder) != 0; break;
                            case 3: currentBtnState = (state.Gamepad.Buttons & GamepadButtonFlags.RightShoulder) != 0; break;
                        }

                        if (currentBtnState)
                        {
                            anyBtn = true;
                            break;
                        }
                    }
                }

                // Edge detection for Toggle Mode (only triggers once per press)
                if (anyBtn && !_wasBtnPressed)
                {
                    _isToggleActive = !_isToggleActive;
                }

                _wasBtnPressed = anyBtn;
                _isBtnPressed = anyBtn;
                _isAnyControllerConnected = anyConnected;

                await Task.Delay(20);
            }
        }

        private void HandlePacket(byte[] data)
        {
            // Now requires 20 bytes (v3 Protocol)
            if (data.Length < 20) return;

            float yawDelta = BitConverter.ToSingle(data, 0);
            float pitchDelta = BitConverter.ToSingle(data, 4);
            float sensitivity = BitConverter.ToSingle(data, 8);
            bool isToggleMode = BitConverter.ToSingle(data, 12) > 0.5f;

            // Extract the requested button (0=L2, 1=R2, 2=L1, 3=R1)
            _activeButton = (int)BitConverter.ToSingle(data, 16);

            int dx = (int)(yawDelta * sensitivity);
            int dy = (int)(pitchDelta * sensitivity);

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
                isMovementAllowed = _isBtnPressed;
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