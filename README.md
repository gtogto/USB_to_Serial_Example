# SimpleUsbTerminal

This Android app provides a line-oriented terminal / console for devices with a serial / UART interface connected with a USB-to-serial-converter.

It supports USB to serial converters based on
- FTDI FT232, FT2232, ...
- Prolific PL2303
- Silabs CP210x
- Qinheng CH340

and devices implementing the USB CDC protocol like
- Arduino using ATmega32U4
- Digispark using V-USB software USB
- BBC micro:bit using ARM mbed DAPLink firmware

## Features

- permission handling on device connection
- foreground service to buffer receive data while the app is rotating, in background, ...

## Credits
The app uses the [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) library.

# USB_to_Serial_Example
usb to serial communication Example for android

## Demo
- version 1.0 -Background color change by 4 actions
![image](https://user-images.githubusercontent.com/30851459/69128306-5489cd00-0aef-11ea-8a91-c50bfec73c86.gif)

- version 1.1 -Color change by x axis (100 colors)

![image](https://user-images.githubusercontent.com/30851459/70115609-9bf18b00-16a3-11ea-85ae-48ee56673bdf.gif)