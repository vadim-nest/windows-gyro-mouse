# GyroMouse 📱🖱️

**Zero-lag Gyro Aiming for Game Streaming & Handheld PCs.**

GyroMouse is a high-performance system that turns your Android device into a precision motion-controller for your Windows PC. It was designed specifically for users who stream games (via Moonlight/Sunshine) to tablets or handhelds (like the ROG Ally, Legion Go, or Steam Deck) and want "PS5-style" gyro aiming for any game.

---

## ✨ Features

- **Esports-Grade Performance:** Uses raw Gyroscope data with a Zero-Allocation UDP pipeline to ensure zero micro-stutters and minimal battery drain.
- **Designed for Moonlight:** Runs as an Android Foreground Service, allowing it to stream motion data in the background while you are playing games in Moonlight or Steam Link.
- **Intelligent Controller Activation:** The mouse only moves when you want it to.
  - **Hold Mode:** Mouse moves only while holding the L2 (Left Trigger).
  - **Toggle Mode:** Click L2 once to start aiming, click again to stop.
- **Multi-Slot Controller Support:** Automatically scans XInput slots 1–4 to find virtual controllers created by streaming software.
- **On-the-Fly Configuration:** Adjust sensitivity, invert pitch, or rotate tracking 90° (for sideways phone mounting) directly from the Android UI.
- **IP Management:** Save multiple PC IP addresses with custom names for easy switching between setups.

---

## 🛠️ How it Works

1. **Android App:** Captures angular velocity from the `TYPE_GYROSCOPE` sensor.
2. **UDP Protocol:** Packages data into a lightweight 16-byte packet (Yaw, Pitch, Sensitivity, Mode) and streams it 50+ times per second.
3. **Windows Receiver:** A WinUI 3 application that listens for UDP packets and monitors Xbox Controller triggers using XInput.
4. **Mouse Injection:** Translates data into smooth cursor movements using the Windows `SendInput` API.

---

## 🚀 Setup Guide

### Windows Receiver

1. **Requirements:** Windows 10/11, Windows App SDK Runtime.
2. **NuGet Packages:** Ensure `SharpDX.XInput` is installed.
3. **Firewall:** Open UDP Port `26760` in Windows Firewall to allow the phone to communicate.
4. **Execution:** Run `GyroMouse.exe` as Administrator (required to inject mouse movement while games are in the foreground).

### Android App

1. **Requirements:** Device with a physical Gyroscope sensor.
2. **Permissions:** Grant "Notification" permissions (required for the background service to stay alive).
3. **Battery:** Set Battery usage to "Unrestricted" for GyroMouse to prevent Android from killing the service during long gaming sessions.

---

## 🎮 Usage Instructions

1. Start Sunshine/Moonlight and connect to your PC.
2. On the PC, launch the GyroMouse Windows Receiver.
3. On the Android device, enter your PC's local IP address.
4. Tap **START TRACKING**.
5. Hold (or toggle) the **L2 Trigger** on your controller to move the mouse with your device!

---

## ⚙️ Technical Specs (v2 Protocol)

GyroMouse uses a custom 16-byte UDP payload for maximum efficiency:

| Offset | Type  | Description                    |
|--------|-------|--------------------------------|
| 0      | Float | Yaw Delta (Radians)            |
| 4      | Float | Pitch Delta (Radians)          |
| 8      | Float | Sensitivity (Multiplier)       |
| 12     | Float | Mode (0.0 = Hold, 1.0 = Toggle)|

---

## 🛡️ Privacy & Safety

- **No Cloud:** All data stays on your local Wi-Fi network.
- **No Drivers:** Uses standard Windows APIs; no risky kernel-level drivers required.
- **Open Source:** You compiled it yourself! No trackers, no ads, no bloat.

---

## 📜 License

Personal use / MIT. Go forth and aim better!
