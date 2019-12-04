package chifoumi.robot.tensorflow

import cnames.structs.TF_Operation
import cnames.structs.TF_Status
import cnames.structs.TF_Tensor
import kotlinx.cinterop.*
import platform.posix.size_t
import tensorflow.*

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

inline fun <T> statusValidated(block: (Status) -> T): T {
    val status = TF_NewStatus()!!
    val result = block(status)
    status.validate()
    return result
}