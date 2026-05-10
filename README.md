# Plato Line Helper

[![Build Debug APK](https://github.com/Devil1716/plato-line-helper/actions/workflows/build.yml/badge.svg)](https://github.com/Devil1716/plato-line-helper/actions/workflows/build.yml)

## What It Does

Plato Line Helper is a transparent Android overlay that draws a slingshot-style aim guide over Plato Table Soccer. Drag on the overlay to preview a physics-based ball path with wall bounces, power feedback, and a highlighted top goal zone.

## How It Works

The overlay runs as a foreground service using `WindowManager` with `TYPE_APPLICATION_OVERLAY`, so it can render a full-screen transparent `TrajectoryView` above the game. The trajectory simulation uses pure Kotlin Euler integration: `FRICTION` gradually slows the ball, `WALL_RESTITUTION` reduces velocity after side and bottom bounces, and `MIN_SPEED` stops the simulation once the shot loses useful energy.

Trajectory dots are drawn individually on Android Canvas instead of using `DashPathEffect`. Fast segments get larger, brighter, more widely spaced dots, while slower segments shrink and fade, making shot power and travel distance easy to read at a glance.

## Setup

1. Clone the repo: `git clone https://github.com/Devil1716/plato-line-helper.git`
2. Open the project in Android Studio.
3. Build and install the debug APK on an Android 8.0+ device.
4. Grant overlay permission when prompted by the app.
5. Tap Start Overlay, then open Plato Table Soccer.

## Roadmap

- [ ] Ball detection via OpenCV
- [ ] Auto-detect field bounds
- [ ] iOS support
