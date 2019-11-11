package chifumi.robot.tensorflow

import chifumi.robot.Operation
import chifumi.robot.Tensor
import chifumi.robot.statusValidated
import cnames.structs.TF_Session
import cnames.structs.TF_Tensor
import kotlinx.cinterop.*
import tensorflow.*

class Session(val graph: Graph) {
    private val inputs = mutableListOf<TF_Output>()
    private val inputValues = mutableListOf<Tensor>()
    private var outputs = mutableListOf<TF_Output>()
    private val outputValues = mutableListOf<Tensor?>()
    private val targets = listOf<Operation>()

    private fun createNewSession(): CPointer<TF_Session> {
        val options = TF_NewSessionOptions()
        val session = statusValidated { TF_NewSession(graph.tensorflowGraph, options, it)!! }
        TF_DeleteSessionOptions(options)
        return session
    }

    private var tensorflowSession: CPointer<TF_Session>? = createNewSession()

    private fun clearInputValues() {
        for (inputValue in inputValues) {
            TF_DeleteTensor(inputValue)
        }

        inputValues.clear()
    }

    private fun clearOutputValues() {
        for (outputValue in outputValues) {
            if (outputValue != null)
                TF_DeleteTensor(outputValue)
        }
        outputValues.clear()
    }

    fun dispose() {
        clearInputValues()
        clearOutputValues()
        clearInputs()
        clearOutputs()

        if (tensorflowSession != null) {
            statusValidated { TF_CloseSession(tensorflowSession, it) }
            statusValidated { TF_DeleteSession(tensorflowSession, it) }
            tensorflowSession = null
        }
    }

    private fun setInputsWithValues(inputsWithValues: List<Pair<Operation, Tensor>>) {
        clearInputValues()
        clearInputs()
        for ((input, inputValue) in inputsWithValues) {
            this.inputs.add(nativeHeap.alloc<TF_Output>().apply { oper = input; index = 0 })
            inputValues.add(inputValue)
        }
    }

    private fun setOutputs(outputs: List<Operation>) {
        clearOutputValues()
        clearOutputs()
        this.outputs = outputs.map { nativeHeap.alloc<TF_Output>().apply { oper = it; index = 0 } }.toMutableList()
    }

    private fun clearOutputs() {
        this.outputs.forEach { nativeHeap.free(it) }
        this.outputs.clear()
    }

    private fun clearInputs() {
        this.inputs.forEach { nativeHeap.free(it) }
        this.inputs.clear()
    }

    operator fun invoke(
        outputs: List<Operation>,
        inputsWithValues: List<Pair<Operation, Tensor>> = listOf()
    ): List<Tensor?> {
        setInputsWithValues(inputsWithValues)
        setOutputs(outputs)

        return invoke()
    }

    operator fun invoke(output: Operation, inputsWithValues: List<Pair<Operation, Tensor>> = listOf()) =
        invoke(listOf(output), inputsWithValues).single()!!

    operator fun invoke(): List<Tensor?> {
        if (inputs.size != inputValues.size) {
            throw Error("Call SetInputs() before Run()")
        }
        clearOutputValues()

        val inputsCArray = if (inputs.any()) nativeHeap.allocArray<TF_Output>(inputs.size) else null

        inputs.forEachIndexed { i, input ->
            inputsCArray!![i].apply {
                oper = input.oper
                index = input.index
            }
        }

        val outputsCArray = if (outputs.any()) nativeHeap.allocArray<TF_Output>(outputs.size) else null

        outputs.forEachIndexed { i, output ->
            outputsCArray!![i].apply {
                oper = output.oper
                index = output.index
            }
        }

        memScoped {
            val outputValuesCArray = allocArrayOfPointersTo<TF_Tensor>(outputs.map { null })

            statusValidated {
                TF_SessionRun(
                    tensorflowSession, null,
                    inputsCArray, inputValues.toCValues(), inputs.size,
                    outputsCArray, outputValuesCArray, outputs.size,
                    targets.toCValues(), targets.size,
                    null, it
                )
            }

            for (index in outputs.indices) {
                outputValues.add(outputValuesCArray[index])
            }
        }

        clearInputValues()

        return outputValues
    }
}