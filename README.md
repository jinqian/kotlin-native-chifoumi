Hand Game robot with Kotlin/Native and Raspberry Pi
===================================================

Slide: https://speakerdeck.com/jinqian/bridge-the-physical-world-kotlin-native-on-raspberry-pi

## Test the project

### Circuit (WIP)

This circuit schema reprents what I use in my project. Choose the pins you want to use and update the code if necessary, as long as it does not burn your Pi!

![circuit](img/circuit-pi.png)

Compile the project:

```
$ ./gradlew build
```

Deploy it on your Pi, make sure you update the SSH configuration in `build.gradle`:

```
$ ./gradlew deployOnPi
```

> Note: you have to configure the [passwordless SSH access](https://www.raspberrypi.org/documentation/remote-access/ssh/passwordless.md) on your Pi to make the SSH plugin work.

## Troubleshooting

### Error when deploying with SSH plugin

If you ran into this problem:

```
* What went wrong:
Execution failed for task ':deployOnPi'.
> com.jcraft.jsch.JSchException: invalid privatekey: [B@57eea633

```

That is caused by the private key headers generated by different versions of OpenSSH. You can refer to [this Stackoverflow post](https://stackoverflow.com/questions/53134212/invalid-privatekey-when-using-jsch) to solve the problem.