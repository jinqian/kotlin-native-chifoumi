package chifumi.robot.tensorflow

import chifumi.robot.Operation
import chifumi.robot.scalarTensor
import chifumi.robot.statusValidated
import cnames.structs.TF_OperationDescription
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import tensorflow.*

class Graph {
    val tensorflowGraph = TF_NewGraph()!!

    inline fun operation(
        type: String,
        name: String,
        initDescription: (CPointer<TF_OperationDescription>) -> Unit
    ): Operation {
        val description = TF_NewOperation(tensorflowGraph, type, name)!!
        initDescription(description)
        return statusValidated { TF_FinishOperation(description, it)!! }
    }

    fun constant(value: Int, name: String = "scalarIntConstant") = operation("Const", name) { description ->
        statusValidated { TF_SetAttrTensor(description, "value", scalarTensor(value), it) }
        TF_SetAttrType(description, "dtype", TF_INT32)
    }

    fun intInput(name: String = "input") = operation("Placeholder", name) { description ->
        TF_SetAttrType(description, "dtype", TF_INT32)
    }

    fun add(left: Operation, right: Operation, name: String = "add") = memScoped {
        val inputs = allocArray<TF_Output>(2)
        inputs[0].apply { oper = left; index = 0 }
        inputs[1].apply { oper = right; index = 0 }

        operation("AddN", name) { description ->
            TF_AddInputList(description, inputs, 2)
        }
    }

    // TODO set unique operation names
    operator fun Operation.plus(right: Operation) = add(this, right)

    inline fun <T> withSession(block: Session.() -> T): T {
        val session = Session(this)
        try {
            return session.block()
        } finally {
            session.dispose()
        }
    }
}