# Plato Line Helper

[![Build Debug APK](https://github.com/Devil1716/plato-line-helper/actions/workflows/build.yml/badge.svg)](https://github.com/Devil1716/plato-line-helper/actions/workflows/build.yml)

Current version: `1.5.0`

## What It Does

Plato Line Helper is a transparent Android overlay that draws a slingshot-style aim guide over Plato Table Soccer. It uses screen capture plus OpenCV to detect the ball and field, then previews a physics-based ball path with wall bounces, power feedback, and a highlighted top goal zone.

## How It Works

The overlay runs as a foreground service using `WindowManager` with `TYPE_APPLICATION_OVERLAY`, so it can render a full-screen transparent `TrajectoryView` above the game. MediaProjection reads screen frames at about 15 fps, OpenCV isolates white/light-grey circles for the ball, and HSV green contour detection locks onto the field bounds after stable calibration.

The trajectory simulation uses pure Kotlin Euler integration: `FRICTION` gradually slows the ball, `WALL_RESTITUTION` reduces velocity after side and bottom bounces, `FLOOR_FRICTION` damps vertical motion, and swipe curve adds lightweight sidespin. Trajectory dots are drawn individually on Android Canvas and change color by result: green for goal, yellow for wall contact, and red when the shot stops short.

## Setup

1. Clone the repo: `git clone https://github.com/Devil1716/plato-line-helper.git`
2. Open the project in Android Studio.
3. Build and install the debug APK on an Android 8.0+ device.
4. Grant overlay permission and approve the screen capture prompt for the current session.
5. Tap Start Overlay, then open Plato Table Soccer.

## In-Game Controls

The main overlay is pass-through all the time, so Plato buttons and menus keep working normally. Use the tiny **LH** bubble to show or hide prediction, and use the small **AIM** pad to preview a shot line from the detected football before taking the shot in Plato.

See [GUIDE.md](GUIDE.md) for the full setup and match-use flow.

## Updates

Tap **Check for Updates** inside the app to open the latest GitHub release and download the newest APK.

## Roadmap

- [x] Ball detection via OpenCV
- [x] Auto-detect field bounds
- [ ] iOS support
