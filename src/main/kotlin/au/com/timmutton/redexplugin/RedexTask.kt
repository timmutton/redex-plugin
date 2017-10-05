package au.com.timmutton.redexplugin

import au.com.timmutton.redexplugin.internal.RedexConfiguration
import au.com.timmutton.redexplugin.internal.RedexConfigurationContainer
import com.android.build.gradle.api.ApplicationVariant
import com.android.builder.model.SigningConfig
import com.google.gson.Gson
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.process.internal.ExecException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * @author timmutton
 */
open class RedexTask: Exec() {
    companion object {
        private val TASK_GROUP = "Optimisation"

        var configFile : String? = null
        var proguardConfigFiles : Array<String>? = null
        var proguardMapFile : String? = null
        var jarFiles : Array<String>? = null
        var keepFile : String? = null
        var otherArgs : String? = null
        var passes: Array<String>? = null

        var sdkDirectory: String? = null
    }

    private var signingConfig: SigningConfig? = null

    @InputFile
    private lateinit var inputFile: File


    @Suppress("UNCHECKED_CAST")
    // Must use DSL to instantiate class, which means I cant pass variant as a constructor argument
    fun initialise(variant: ApplicationVariant) {
        group = TASK_GROUP
        description = "Run Redex tool on your ${variant.name.capitalize()} apk"
        signingConfig = variant.buildType.signingConfig
        mustRunAfter(variant.assemble)

        val output = variant.outputs.first { it.outputFile.name.endsWith(".apk") }
        inputFile = output.outputFile

        if (passes != null && configFile != null) {
            throw IllegalArgumentException(
                "Cannot specify both passes and configFile");
        }
    }

    fun addFileArg(option : String, path : String ) {
        val file = File(project.projectDir, path)
        if (!file.exists()) {
            throw FileNotFoundException(
                "Could not find file at path: " +
                file.absolutePath)
        }
        args(option, file.absolutePath)
    }

    override fun exec() {
        sdkDirectory?.apply {
            environment("ANDROID_SDK", sdkDirectory)
        }

        passes?.apply {
            if (passes!!.isNotEmpty()) {
                val redexConfig = Gson().toJson(RedexConfigurationContainer(RedexConfiguration(passes!!)))
                val config = File(project.buildDir, "redex.config")
                config.createNewFile()
                val writer = FileWriter(config)
                val configString = Gson().toJson(redexConfig)
                writer.write(configString.substring(1, configString.length - 1).replace("\\", ""))
                writer.close()
                args("-c", config.absolutePath)
            }
        }

        configFile?.apply {
            addFileArg("-c", configFile!!)
        }

        proguardConfigFiles?.apply{
            for (path in proguardConfigFiles!!) {
                addFileArg("-P", path)
            }
        }

        proguardMapFile?.apply{
            addFileArg("-m", proguardMapFile!!)
        }
        
        jarFiles?.apply{
            for (path in jarFiles!!) {
                addFileArg("-j", path)
            }
        }

        keepFile?.apply{
            addFileArg("-k", keepFile!!)
        }

        otherArgs?.apply{
            args("", otherArgs!!)
        }

        signingConfig?.apply {
            args("--sign",
                    "--keystore", signingConfig!!.storeFile.absolutePath,
                    "--keyalias", signingConfig!!.keyAlias,
                    "--keypass", signingConfig!!.keyPassword)
        }

        val outputFile = File(inputFile.toString())

        val unredexed = File(inputFile.toString().replace(".apk", "-unredexed.apk"))
        Files.move(inputFile.toPath(), unredexed.toPath(), StandardCopyOption.REPLACE_EXISTING)
        inputFile = unredexed

        args("-o", "$outputFile", "$inputFile")
        executable("redex")

        var showStats = true
        var startingMethods = 0
        var startingFields = 0
        var startingSize = 0

        val originalDexData = DexFile.extractDexData(inputFile)
        try {
            startingMethods = originalDexData.sumBy { it.data.methodRefs.size }
            startingFields = originalDexData.sumBy { it.data.fieldRefs.size }
            startingSize = inputFile.length().toInt()

            logger.log(LogLevel.LIFECYCLE, "\nBefore redex:")
            logger.log(LogLevel.LIFECYCLE, "\t$startingMethods methods")
            logger.log(LogLevel.LIFECYCLE, "\t$startingFields fields")
            logger.log(LogLevel.LIFECYCLE, "\t$startingSize bytes")
        } catch(e: Exception) {
            showStats = false
        } finally {
            originalDexData.forEach { it.dispose() }
        }

        try {
            super.exec()

            if(showStats) {
                val newDexData = DexFile.extractDexData(outputFile)
                try {
                    val methods = newDexData.sumBy { it.data.methodRefs.size }
                    val methodPercentage = "%.2f".format(methods.toFloat() / startingMethods * 100f)
                    val fields = newDexData.sumBy { it.data.fieldRefs.size }
                    val fieldPercentage = "%.2f".format(fields.toFloat() / startingFields * 100f)
                    val size = outputFile.length().toInt()
                    val sizePercentage = "%.2f".format(size.toFloat() / startingSize * 100f)

                    logger.log(LogLevel.LIFECYCLE, "After redex:")
                    logger.log(LogLevel.LIFECYCLE, "\t$methods methods (%$methodPercentage of original)")
                    logger.log(LogLevel.LIFECYCLE, "\t$fields fields (%$fieldPercentage of original)")
                    logger.log(LogLevel.LIFECYCLE, "\t$size bytes (%$sizePercentage of original)")
                } finally {
                    newDexData.forEach { it.dispose() }
                }
            }
        } catch (e: ExecException) {
            if (e.message != null && e.message!!.contains("A problem occurred starting process")) {
                throw ExecException("A problem occurred starting Redex. " +
                        "Ensure you have installed Redex using the instructions at https://github.com/facebook/redex")
            } else {
                throw e
            }
        }
    }
}
