package au.com.timmutton.redexplugin

import au.com.timmutton.redexplugin.internal.RedexConfiguration
import au.com.timmutton.redexplugin.internal.RedexConfigurationContainer
import com.android.build.gradle.api.ApplicationVariant
import com.android.builder.model.SigningConfig
import com.google.gson.Gson
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFile
import org.gradle.process.internal.ExecException
import java.io.File
import java.io.FileWriter
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * @author timmutton
 */
open class RedexTask: Exec() {
    private var signingConfig: SigningConfig? = null
    private var configFile : File? = null
    private var proguardConfigFiles : List<File>? = null
    private var proguardMapFile : File? = null
    private var jarFiles : List<File>? = null
    private var keepFile : File? = null
    private var otherArgs : String? = null
    private var passes: List<String>? = null
    private var showStats: Boolean = true
    private var sdkDirectory: File? = null

    @InputFile
    private lateinit var inputFile: File

    private var mappingFile: File? = null

    @Suppress("UNCHECKED_CAST")
    // Must use DSL to instantiate class, which means I cant pass variant as a constructor argument
    fun initialise(variant: ApplicationVariant, extension: RedexExtension) {
        description = "Run Redex tool on your ${variant.name.capitalize()} apk"

        configFile = extension.configFile
        proguardConfigFiles = extension.proguardConfigFiles /*?: variant.let {
            val proguardFiles = it.buildType.proguardFiles.toMutableList()
            proguardFiles.addAll(it.mergedFlavor.proguardFiles)
            proguardFiles
        }*/

        proguardMapFile = extension.proguardMapFile /*?: variant.mappingFile*/
        jarFiles = extension.jarFiles
        keepFile = extension.keepFile /*?: variant.let {
            it.buildType.multiDexKeepProguard
            // TODO: add support for the merged flavor keep file
//            it.mergedFlavor.multiDexKeepProguard
        }*/
        otherArgs = extension.otherArgs
        passes = extension.passes
        showStats = extension.showStats
        signingConfig = variant.buildType.signingConfig
        sdkDirectory = extension.sdkDirectory

        dependsOn(variant.assemble)
        mustRunAfter(variant.assemble)

        if(variant.buildType.isMinifyEnabled) {
            variant.assemble.finalizedBy(this)
        }

        val output = variant.outputs.first { it.outputFile.name.endsWith(".apk") }
        inputFile = output.outputFile

        if (passes != null && configFile != null) {
            throw IllegalArgumentException(
                "Cannot specify both passes and configFile");
        }
    }

    override fun exec() {
        sdkDirectory?.let {
            environment("ANDROID_SDK", it)
        }

        passes?.let {
            val redexConfig = Gson().toJson(RedexConfigurationContainer(RedexConfiguration(it)))
            val config = File(project.buildDir, "redex.config")
            config.createNewFile()
            val writer = FileWriter(config)
            val configString = Gson().toJson(redexConfig)
            writer.write(configString.substring(1, configString.length - 1).replace("\\", ""))
            writer.close()
            args("-c", config.absolutePath)
        }

        configFile?.let {
            if(it.exists()) {
                args("-c", it.absolutePath)
            }
        }

        proguardConfigFiles?.forEach {
            if(it.exists()) {
                args("-P", it.absolutePath)
            }
        }

        proguardMapFile?.let {
            if(it.exists()) {
                args("-m", it.absolutePath)
            }
        }
        
        jarFiles?.forEach {
            if(it.exists()) {
                args("-j", it.absolutePath)
            }
        }

        keepFile?.let {
            if(it.exists()) {
                args("-k", it.absolutePath)
            }
        }

        otherArgs?.let {
            args("", it)
        }

        signingConfig?.let {
            args("--sign",
                    "--keystore", it.storeFile.absolutePath,
                    "--keyalias", it.keyAlias,
                    "--keypass", it.keyPassword)
        }

        val outputFile = File(inputFile.toString())

        val unredexed = File(inputFile.toString().replace(".apk", "-unredexed.apk"))
        Files.move(inputFile.toPath(), unredexed.toPath(), StandardCopyOption.REPLACE_EXISTING)
        inputFile = unredexed

        args("-o", "$outputFile", "$inputFile")
        executable("redex")

        try {
            super.exec()

            if(showStats) {
                logStats(outputFile)
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

    private fun logStats(outputFile: File) {
        val originalDexData = DexFile.extractDexData(inputFile)
        val newDexData = DexFile.extractDexData(outputFile)

        try {
            val startingMethods = originalDexData.sumBy { it.data.methodRefs.size }
            val startingFields = originalDexData.sumBy { it.data.fieldRefs.size }
            val startingSize = inputFile.length().toInt()

            logger.log(LogLevel.LIFECYCLE, "\nBefore redex:")
            logger.log(LogLevel.LIFECYCLE, "\t$startingMethods methods")
            logger.log(LogLevel.LIFECYCLE, "\t$startingFields fields")
            logger.log(LogLevel.LIFECYCLE, "\t$startingSize bytes")

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
            originalDexData.forEach { it.dispose() }
            newDexData.forEach { it.dispose() }
        }
    }
}
