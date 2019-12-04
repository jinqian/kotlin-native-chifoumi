package chifoumi.robot.tensorflow

import kotlinx.cinterop.*
import platform.linux.malloc
import platform.posix.*
import tensorflow.*

class TensorFlowInterpreter {

    fun hello() {
        println("Hello, TensorFlow ${TF_Version()!!.toKString()}!")
        val result = Graph().run {
            val input = intInput()

            withSession {
                invoke(
                    input + constant(2),
                    inputsWithValues = listOf(input to scalarTensor(3))
                ).scalarIntValue
            }
        }

        println("3 + 2 is $result.")

        classifyImage()
    }

    fun classifyImage() {

        // load graph
        val fileName = "/home/pi/qian/playground/mobilenet_v2_1.4_224_frozen.pb"
        val graph_def = readFile(fileName)

        val graph = TF_NewGraph()
        val status = TF_NewStatus()
        val opts = TF_NewImportGraphDefOptions()

        TF_GraphImportGraphDef(graph, graph_def, opts, status)
        TF_DeleteImportGraphDefOptions(opts)

        if (TF_GetCode(status) != TF_OK) {
            println("ERROR: Unable to import graph %s")
            println(TF_Message(status))
        } else {
            println("Successfully imported graph");
        }
    }

    fun readFile(fileName: String): CPointer<TF_Buffer> {
        val file = fopen(fileName, "rb")
        if (file == null) {
            perror("cannot open input file $fileName")
        }

        fseek(file, 0, SEEK_END);
        val fsize = ftell(file);
        fseek(file, 0, SEEK_SET);  // same as rewind(f);

        val data = malloc(fsize.convert())!!
        fread(data, fsize.convert(), 1, file);
        fclose(file)

        val buffer = TF_NewBuffer()!!

        buffer.pointed.apply {
            this.data = data
            this.length = fsize.convert()
            this.data_deallocator = staticCFunction { data, size ->
                free(data)
            }
        }

        return buffer
    }
}