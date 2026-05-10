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

- **LH bubble**: tap to show or hide the guide.
- Drag the **LH bubble** to move it away from important game controls.
- **AIM pad**: drag inside this small pad to preview shot direction and power without touching the Plato field.
- Use the persistent Android notification **Stop** action to remove the overlay.
- The full-screen overlay is always touch-through; it does not capture match taps.

## How To Use In A Match

1. Wait until you are on the actual soccer field, not the Plato menu.
2. Make sure the **LH** bubble is green. Tap it if the guide is off.
3. Drag the **LH** bubble to an empty edge if it covers the match.
4. Drag inside the **AIM** pad. The line starts from the detected football and shows the predicted route.
5. Release the pad, then repeat the same shot direction in Plato.
6. The helper also estimates the path after the real ball starts moving.
7. Read the guide color:
   - Green: predicted path reaches the goal mouth.
   - Yellow: predicted path hits a wall.
   - Red: predicted path stops short.

## If It Feels Broken

- If Plato buttons do not work, stop the overlay from the Android notification. The guide layer is coded as touch-through, so any remaining issue is usually the small bubble sitting under your finger.
- If the bubble covers the match, drag it to another edge.
- If the AIM pad covers the match, use it only while lining up, then ignore it while playing; the Plato field itself remains touchable.
- If the guide appears on menus, tap **LH** to hide it until the actual match field is open.
- If the ball ring is missing, improve contrast: avoid busy screens, wait until the ball is visible, and restart the overlay if detection gets stuck.
- If Android asks for screen capture again, approve it. That is required by the system.

## Accuracy Tips

- Use this as a lineup assistant, not an autoplayer.
- Predictions appear after the ball starts moving, because this version does not steal touch input from Plato.
- Straight shots are easier to predict than crowded rebounds.
- The helper improves aiming feedback, but timing and execution still decide the match.
