# PRECORDER

Fabric client mod project for Minecraft Java 1.21.11 / Java 21.

## Current milestone

This is a **buildable project scaffold**, not a finished recorder. It registers F9 and displays a status message. The actual frame capture, MP4 encoder, game-audio capture, microphone capture, Android MediaCodec integration, settings UI, and output-folder management are not implemented yet.

## Build

Run `gradle build` with JDK 21 and internet access. The JAR is produced in `build/libs/`.

## GitHub Actions

Push this project to the `main` branch, or use Actions → Build PRECORDER → Run workflow. Download the `PRECORDER-build` artifact from the completed run.

## Android / Hyper Launcher

A successful desktop CI build does not prove compatibility with Hyper Launcher 3. Install and test the JAR in a separate instance first. Android audio/video capture needs a dedicated Android bridge and device testing.
