package au.com.timmutton.redexplugin

import au.com.timmutton.redexplugin.internal.RedexConfiguration
import au.com.timmutton.redexplugin.internal.RedexConfigurationContainer
import com.android.build.gradle.api.ApplicationVariant
import com.android.builder.model.SigningConfig
import com.google.gson.Gson
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.process.internal.ExecException
import java.io.File
import java.io.FileWriter

/**
 * @author timmutton
 */
open class RedexTask : Exec() {
    companion object {
        private val TASK_GROUP = "Optimisation"

        var passes: Array<String>? = null
        var sdkDirectory: String? = null
    }

    private var signingConfig: SigningConfig? = null

    @InputFile
    private lateinit var inputFile: File

    @OutputFile
    private lateinit var outputFile: File

    // Must use DSL to instantiate class, which means I cant pass variant as a constructor argument
    fun initialise(variant: ApplicationVariant) {
        group = TASK_GROUP
        description = "Run Redex tool on your ${variant.name.capitalize()} apk"
        signingConfig = variant.buildType.signingConfig
        mustRunAfter(variant.assemble)

        val output = variant.outputs[0]
        inputFile = output.outputFile
        outputFile = File(output.outputFile.toString().replace(".apk", "-redex.apk"))
    }

    override fun exec() {
        sdkDirectory?.apply {
            environment("ANDROID_SDK", sdkDirectory)
        }

        passes?.apply {
            if(passes!!.size > 0) {
                val redexConfig = Gson().toJson(RedexConfigurationContainer(RedexConfiguration(passes!!)))
                val configFile = File(project.buildDir, "redex.config")
                configFile.createNewFile()
                val writer = FileWriter(configFile)
                val configString = Gson().toJson(redexConfig)
                writer.write(configString.substring(1, configString.length - 1).replace("\\", ""))
                writer.close()
                args("-c", configFile.absolutePath)
            }
        }

        signingConfig?.apply {
            args("--sign",
                    "--keystore", signingConfig!!.storeFile.absolutePath,
                    "--keyalias", signingConfig!!.keyAlias,
                    "--keypass", signingConfig!!.keyPassword)
        }

        args("-o", "$outputFile", "$inputFile")
        executable("redex")

        try {
            super.exec()
        } catch (e: ExecException) {
            if(e.message != null &&  e.message!!.contains("A problem occurred starting process")) {
                throw ExecException("A problem occurred starting Redex. " +
                        "Ensure you have installed Redex using the instructions at https://github.com/facebook/redex")
            } else {
                throw e
            }
        }
    }
}