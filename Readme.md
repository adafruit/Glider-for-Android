> **Work-in-progress.** 

> This repository contains a work-in-progress version of File Glider for Android.
Source code for the iOS app is here: [https://github.com/adafruit/Glider-for-iOS](https://github.com/adafruit/Glider-for-iOS)**



## Introduction

Wirelessly transfer files to and from file transfer ready Bluetooth
Low Energy (BLE) firmware. Browse and edit files from within File
Glider or use the Files app integration to access the files from other
apps. Multiple devices can be managed at once and access can be shared
amongst multiple apps.

Compatible devices:
* nrf52840 boards running CircuitPython 7.0.0+

The file transfer protocol is open so you can use File Glider with
custom firmware. See GitHub for protocol details:
[https://github.com/adafruit/Adafruit_CircuitPython_BLE_File_Transfer](https://github.com/adafruit/Adafruit_CircuitPython_BLE_File_Transfer)



## Intalling the debug APK

#### Android Permissions to sideload apps

Installing an APK from outside the Google PlayStore needs 2 permissions:

- Grant permission to the browser or files app to install apps (depending of the manufacturer you can unzip and install directly from the browser or you need to use the files app)
 
- Grant permisssion to install apps from unknown sources

Both permissions can be found on the device Settings but usually they will asked interactively when the app is downloaded (which is easier than to find them on the Settings menu that can be different for each manufacturer)

### Install steps

1- Download the "Glider debug APK" artifact from GitHub actions using the browser on your Android device

> **Note:** The browser may ask for permission to download the file. Grant it.

2- Click on the downloaded zip file (there should appear a notification with the direct link to the file when the download finishes) and extract the contents (an APK file)

> **Note:** The zip extraction works directly on devices by some manufacturers (like Samsung). If it complains that the file can not be opened, go to the Files apps and open it from there to extract the zip contents.

3- Click on the apk file and it should start the installation

> **Install permission:** A dialog may appear saying that an app from unknown sources can not be installed. Click on the button on that dialog that directs to Settings to grant that permission.  
 
> **Play Protect warning:** The installation can be blocked by "Play Protect". A dialog will be shown saying that Play Protect does not recognize the developer for this app. Click "Install" (Warning: there is an "Accept" button that will not install the app, because it will accept the block suggested by Play Protect)

4- Glider should be installed now

> **Note:** The first time running Glider it will ask for Bluetooth permission (or location permission on devices with Android less than 12). Please grant it.




