# PocketAeye

This Android app uses a pre-trained object detection model to perform real-time object detection using the device's camera.

![Alt text](/Screenshot_20240111_160127_PocketAeye.jpg)

## Features

- Realtime object detection using a pre-trained model.
- Text-to-speech announcements for detected objects.
- Capture image functionality.

## Prerequisites

- Android Studio
- Android device or emulator with a camera

## Getting Started

1. Clone the repository:

    ```bash
    git clone https://github.com/your-username/realtime-object-detection-app.git
    ```

2. Open the project in Android Studio.

3. Build and run the app on your Android device or emulator.

## Permissions

Ensure that the app has the necessary camera permissions. If not, the app will request the user's permission.

## Usage

1. Launch the app on your device.

2. Grant camera permission if prompted.

3. Press the screen to start object detection.

4. Detected objects will be highlighted on the camera preview, and their names will be announced using text-to-speech.


## Configuration

- Model: The app uses the SsdMobilenetV1 object detection model. You can replace it with a different model if needed.

- Labels: Labels for object classes are loaded from the "labels.txt" file. Ensure that it is present and correctly formatted.

## Troubleshooting

If you encounter issues, ensure that the necessary dependencies are installed, and the camera is functioning correctly on your device or emulator.

## Contributing

Feel free to contribute to this project by opening issues or submitting pull requests.
