package chifumi.robot

import kotlinx.cinterop.*
import pca9685.PCA9685_initPWM
import pca9685.PCA9685_openI2C
import pca9685.PCA9685_setPWMVals
import pca9685._PCA9685_CHANS
import pigpio.*
import kotlin.system.getTimeMillis

fun main() {

    initGPIO()

    initPWMDriver()

    setupButton()

    blinkLed()

    takePicture()
}

private fun initGPIO() {
    if (gpioInitialise() < 0) {
        println("GPIO Error initialising")
        return
    }
}

private fun initPWMDriver() {
    println("init PWM")
    val adapterNumber = 1
    val fileDescriptor = PCA9685_openI2C(
        adapterNumber.toUByte(),
        I2C_SERVO_ADDRESS.toUByte()
    )
    // TODO check the init result, 0 for success, other value for failure
    PCA9685_initPWM(
        fileDescriptor,
        I2C_SERVO_ADDRESS.toUByte(),
        PWM_FREQUENCY.toUInt()
    )

    // https://jonnyzzz.com/blog/2019/01/14/kn-intptr/
    memScoped {
        val setOnValues = allocArray<UIntVar>(_PCA9685_CHANS)
        val setOffValues = allocArray<UIntVar>(_PCA9685_CHANS)

        for (i in 0..15) {
            setOnValues[i] = SERVO_ACTIVATED_ANGLE.toUInt()
        }
        for (i in 0..15) {
            setOffValues[i] = SERVO_NON_ACTIVATED_ANGLE.toUInt()
        }

        PCA9685_setPWMVals(fileDescriptor, I2C_SERVO_ADDRESS.toUByte(), setOnValues, setOffValues)

        var servoMotorCount = 3
        while (servoMotorCount > 0) {
            PCA9685_setPWMVals(fileDescriptor, I2C_SERVO_ADDRESS.toUByte(), setOffValues, setOnValues)
            gpioSleep(PI_TIME_RELATIVE, 1, 0)
            PCA9685_setPWMVals(fileDescriptor, I2C_SERVO_ADDRESS.toUByte(), setOnValues, setOffValues)
            servoMotorCount--
        }
    }
}

val onButtonPressed = staticCFunction<Int, Int, UInt, Unit> {gpio, level, tick ->
    when (level) {
        0 -> {
            println("Button Pressed down, level 0")
            // TODO perform action here will lead to signal terminated?
        }
        1 -> println("Button Released, level 1")
        2 -> println("Button GPIO timeout, no level change")
    }
}


private fun setupButton() {
    val buttonPort = GPIO_BUTTON.toUInt()
    initPortWithMode(buttonPort, PI_INPUT)

    gpioSetAlertFunc(buttonPort, onButtonPressed)
}

fun blinkLed() {
    val ledPort = GPIO_LED.toUInt()
    initPortWithMode(ledPort, PI_OUTPUT)

    println("Start blinking LED")
    var blinkCount = 3
    while (blinkCount > 0) {
        gpioWrite(ledPort, PI_LOW)
        gpioSleep(PI_TIME_RELATIVE, 0, 500000)
        gpioWrite(ledPort, PI_HIGH)
        gpioSleep(PI_TIME_RELATIVE, 0, 500000)
        blinkCount--
    }
}

fun takePicture() {
    println("Take picture now!")
    platform.posix.system("raspistill -t 3000 -vf -hf -o camera-output/${getTimeMillis()}.jpg")
}

fun initPortWithMode(port: UInt, mode: Int) {
    if (gpioSetMode(port, mode.toUInt()) < 0) {
        println("Could not set mode for GPIO$port")
        return
    }
}