package chifumi.robot

import chifumi.robot.tensorflow.Graph
import cnames.structs.TF_Operation
import cnames.structs.TF_Status
import cnames.structs.TF_Tensor
import kotlinx.cinterop.*
import pca9685.PCA9685_initPWM
import pca9685.PCA9685_openI2C
import pca9685.PCA9685_setPWMVals
import pca9685._PCA9685_CHANS
import pigpio.*
import platform.posix.size_t
import tensorflow.*
import kotlin.system.getTimeMillis

fun main() {

    helloTensorFlow()

    initGPIO()

    initPWMDriver()

    setupButton()

    blinkLed()

    takePicture()
}

// region gpio

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

    setPWMValues(fileDescriptor)
}

fun setPWMValues(fileDescriptor: Int) {
    // https://jonnyzzz.com/blog/2019/01/14/kn-intptr/
    memScoped {
        val onValues = allocArray<UIntVar>(_PCA9685_CHANS)
        val offValues = allocArray<UIntVar>(_PCA9685_CHANS)

        for (i in 0 until _PCA9685_CHANS - 1) {
            onValues[i] = SERVO_ON_ANGLE.toUInt()
        }
        for (i in 0 until _PCA9685_CHANS - 1) {
            offValues[i] = SERVO_OFF_ANGLE.toUInt()
        }

        PCA9685_setPWMVals(fileDescriptor, I2C_SERVO_ADDRESS.toUByte(), onValues, offValues)

        // calibrate servomotors
        var calibrationCount = 3
        while (calibrationCount > 0) {
            PCA9685_setPWMVals(fileDescriptor, I2C_SERVO_ADDRESS.toUByte(), offValues, onValues)
            gpioSleep(PI_TIME_RELATIVE, 1, 0)
            PCA9685_setPWMVals(fileDescriptor, I2C_SERVO_ADDRESS.toUByte(), onValues, offValues)
            calibrationCount--
        }
    }
}

fun setPWMValuesAlso(fileDescriptor: Int) {
    val onValues = UIntArray(_PCA9685_CHANS)
    for (i in 0 until _PCA9685_CHANS - 1) {
        onValues[i] = SERVO_ON_ANGLE.toUInt()
    }
    val offValues = UIntArray(_PCA9685_CHANS)
    for (i in 0 until _PCA9685_CHANS - 1) {
        offValues[i] = SERVO_OFF_ANGLE.toUInt()
    }

    PCA9685_setPWMVals(
        fileDescriptor, I2C_SERVO_ADDRESS.toUByte(),
        onValues.toCValues(), offValues.toCValues()
    )
}

val onButtonPressed = staticCFunction<Int, Int, UInt, Unit> { gpio, level, tick ->
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

private fun initPortWithMode(port: UInt, mode: Int) {
    if (gpioSetMode(port, mode.toUInt()) < 0) {
        println("Could not set mode for GPIO$port")
        return
    }
}

// endregion

// region camera

fun takePicture() {
    println("Take picture now!")
    platform.posix.system("mkdir -p camera-output && raspistill -t 3000 -vf -hf -o camera-output/${getTimeMillis()}.jpg")
}

// endregion

// region tensorflow

private fun helloTensorFlow() {
    println("Hello, TensorFlow ${TF_Version()!!.toKString()}!")
    val result = Graph().run {
        val input = intInput()

        withSession { invoke(input + constant(2), inputsWithValues = listOf(input to scalarTensor(3))).scalarIntValue }
    }

    println("3 + 2 is $result.")
}

typealias Status = CPointer<TF_Status>
typealias Operation = CPointer<TF_Operation>
typealias Tensor = CPointer<TF_Tensor>

val Status.isOk: Boolean get() = TF_GetCode(this) == TF_OK
val Status.errorMessage: String get() = TF_Message(this)!!.toKString()
fun Status.delete() = TF_DeleteStatus(this)
fun Status.validate() {
    try {
        if (!isOk) {
            throw Error("Status is not ok: $errorMessage")
        }
    } finally {
        delete()
    }
}

inline fun <T> statusValidated(block: (Status) -> T): T {
    val status = TF_NewStatus()!!
    val result = block(status)
    status.validate()
    return result
}

fun scalarTensor(value: Int): Tensor {
    val data = nativeHeap.allocArray<IntVar>(1)
    data[0] = value

    return TF_NewTensor(
        TF_INT32,
        dims = null, num_dims = 0,
        data = data, len = IntVar.size.convert(),
        deallocator = staticCFunction { dataToFree, _, _ -> nativeHeap.free(dataToFree!!.reinterpret<IntVar>()) },
        deallocator_arg = null
    )!!
}

val Tensor.scalarIntValue: Int
    get() {
        if (TF_INT32 != TF_TensorType(this) || IntVar.size.convert<size_t>() != TF_TensorByteSize(this)) {
            throw Error("Tensor is not of type int.")
        }
        if (0 != TF_NumDims(this)) {
            throw Error("Tensor is not scalar.")
        }

        return TF_TensorData(this)!!.reinterpret<IntVar>().pointed.value
    }

// endregion