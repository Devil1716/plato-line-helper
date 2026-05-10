# Plato Line Helper Guide

This app is an overlay aim assistant. It does not modify Plato, hook into the game, or guarantee wins, but it can help you line up shots more consistently when the screen detection is calibrated.

## First-Time Setup

1. Install the latest APK from the GitHub releases page.
2. Open **Plato Line Helper**.
3. Tap **Grant Overlay Permission** and allow drawing over other apps.
4. Return to the app and tap **Start Overlay**.
5. Approve the Android screen capture prompt. Android requires this every app restart.
6. Open Plato Table Soccer.

## Controls During Plato

- **AIM**: turns on aim preview mode.
- **ON**: means Aim Mode is active and the overlay is capturing your drag.
- **STOP**: removes the overlay completely.
- When Aim Mode is off, taps pass through to Plato normally.

## How To Use In A Match

1. Wait until you are on the actual soccer field, not the Plato menu.
2. Let the helper calibrate for a moment. The green ball ring means it has detected the ball.
3. Drag the **AIM / STOP** panel to an empty edge if it covers anything important.
4. Tap **AIM** on the floating panel.
5. Pull backward from the ball direction, like a slingshot. The guide starts from the detected ball and predicts where that hit will travel.
6. Release. Aim Mode auto-disables after a short moment so Plato is tappable again.
7. Repeat the same pull in Plato while pass-through mode is active.
8. Read the guide color:
   - Green: predicted path reaches the goal mouth.
   - Yellow: predicted path hits a wall.
   - Red: predicted path stops short.

## If It Feels Broken

- If Plato buttons do not work, tap **STOP** or wait for Aim Mode to auto-disable.
- If the control covers the match, drag the control panel to another edge.
- If the guide appears on menus, enter the actual match field before relying on it.
- If the ball ring is missing, improve contrast: avoid busy screens, wait until the ball is visible, and restart the overlay if detection gets stuck.
- If Android asks for screen capture again, approve it. That is required by the system.

## Accuracy Tips

- Use this as a lineup assistant, not an autoplayer.
- Short, straight drags produce the most stable predictions.
- Curved swipes add sidespin, so use them only when you need a bend.
- The helper improves aiming feedback, but timing and execution still decide the match.
