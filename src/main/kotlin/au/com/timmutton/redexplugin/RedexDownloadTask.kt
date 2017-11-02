package au.com.timmutton.redexplugin

import java.util.regex.Pattern
import java.util.regex.Matcher
import java.nio.file.Files
import java.nio.charset.Charset
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.ByteArrayOutputStream
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction


open class RedexDownloadTask : DefaultTask() {
    val DEBUG = true
    var requestedVersion : String? = "latest"
    public val buildDir = project.buildDir.getPath()

    // if dest is null, return the data
    // if dest is not null, write it to dest
    private fun download(url : String, dest : File?) : ByteArray? {
        var output : ByteArray? = null
        val (_,_, result) = Fuel.get(url).response()
        val (data : ByteArray?, error) = result
        if (error != null) {
            throw error
        }
        if (data == null) {
            throw IOException("No data from download")
        }
        if (dest == null) {
            output = data
        } else {
            dest.writeBytes(data)
        }
        return output
    }

    // releases/latest is a webpage for the most recent release
    // of redex. Search through the html for the name of the
    // tag of this release.
    private fun getLatestRedexTag() : String {
        val latest = "https://github.com/facebook/redex/releases/latest"
        val input = download(latest, null)!!.toString(Charset.defaultCharset())
        val pattern = Pattern.compile(
            "/facebook/redex/releases/tag/(v\\d+\\.\\d+\\.\\d+)")
        val matcher = pattern.matcher(input)
        matcher.find()
        return matcher.group(1)
    }

    public fun initialise(ext : RedexExtension) : File? {
        requestedVersion = ext.version
        val (redex, _) = getRedexExecutableFile()
        return redex
    }

    @TaskAction
    // download redex from the github release page
    public fun run() {
        if (requestedVersion == null) {
            return
        }
        val (redex, url) = getRedexExecutableFile()
        if (url != null && !redex.exists()) {
            // Only download if we don't already have it
            download(url, redex)
            redex.setExecutable(true)
        }
        if (DEBUG) {
            println("Redex downloaded to ${redex.getAbsolutePath()}")
        }
    }

    data class Pair<T1, T2>(val first : T1, val second : T2)
    fun getRedexExecutableFile() : Pair<File?, String?> {
        val tag = if (requestedVersion == "latest") getLatestRedexTag()
                  else requestedVersion
        val os = getOS()
        if (os != null) {
            val redex_exec = "redex_${getOS()!!}"
            val url = "https://github.com/facebook/redex/releases/download/$tag/$redex_exec"
            return Pair(File("$buildDir/${redex_exec}_$tag"), url)
        }
        return Pair(null, null)
    }

    fun getOS() : String? {
        if (Os.isFamily(Os.FAMILY_UNIX)) {
            // TODO support other architectures (32bit, non intel, etc.)
            return "linux_x86_64"
        } else if (Os.isFamily(Os.FAMILY_MAC)) {
            return "macos_x86_64"
        }
        System.err.println(
            "Your platform isn't supported (yet) for downloading redex." +
            "Please follow instructions at https://github.com/facebok/redex." +
            "Assuming redex is in your PATH")
        return null
    }
}

