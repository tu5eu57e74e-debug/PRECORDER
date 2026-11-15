PRECORDER

<p align="center">
  **A configurable in-game video recorder for Minecraft Java Edition.**
</p><p align="center">
  Fabric · FFmpeg · H.264 · MP4
</p>---

Overview

PRECORDER brings video recording controls directly into Minecraft. Start, pause, and stop recordings without leaving your world, then find your MP4 videos in the active game instance's recording folder.

The recorder combines configurable capture quality, simple in-game controls, and an FFmpeg-based encoding pipeline.

Features

- In-game controls — open the recorder menu with "F8", start or stop recording with "F9", and pause or resume with "F10".
- MP4 video output — H.264 video encoded in an MP4 container.
- Selectable resolution — 480p, 720p, or 1080p.
- Selectable frame rate — 30, 60, or 120 FPS.
- Adjustable bitrate — configure video bitrate from 2 to 50 Mbps.
- HUD option — choose whether the Minecraft HUD appears in recordings.
- Background encoding — captured frames are processed by a separate recording worker.
- Organized recordings — videos are saved in the "p-recordings" folder associated with the active game instance.
- FFmpeg encoder check — checks that the FFmpeg native library and H.264 encoder are available.

Controls

Key| Action
"F8"| Open PRECORDER menu
"F9"| Start / Stop recording
"F10"| Pause / Resume recording

You can also control recording and adjust settings through the in-game menu.

Recording Settings

Setting| Options
Resolution| 480p · 720p · 1080p
Frame rate| 30 · 60 · 120 FPS
Video bitrate| 2–50 Mbps
HUD| Show / Hide

Higher resolutions, frame rates, and bitrates require more processing power and storage space.

Output

Recordings are saved in:

p-recordings/

Each recording is exported as an ".mp4" file.

Requirements

- Minecraft Java Edition 1.21.11
- Java 21
- Fabric Loader 0.18.0 or newer
- Fabric API for Minecraft 1.21.11
- Android ARM64 FFmpeg native build for the current Android-targeted package

Build from Source

1. Install JDK 21.
2. Clone this repository.
3. Run the Gradle build:

./gradlew build

On Windows:

gradlew.bat build

The compiled mod JAR is generated in:

build/libs/

GitHub Actions

The repository includes a GitHub Actions build workflow.

Open the Actions tab, run the build workflow, and download the "PRECORDER-build" artifact after a successful run.

---

<p align="center">
  **PRECORDER** · Record your moments in-game.
</p>
