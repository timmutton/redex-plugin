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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonElement
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction


open class RedexDownloadTask : DefaultTask() {
    var requestedVersion : String? = "latest"
    public val buildDir = project.buildDir.getPath()

    // Returns the location where redex will be downloaded
    // after this task is run
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
        if (redex == null || url == null) {
            return
        }
        if (!redex.exists()) {
            // Only download if we don't already have it
            downloadFile(url, redex)
            redex.setExecutable(true)
        }
    }

    private fun downloadFile(url : String, dest : File) {
        val (_,_, result) = Fuel.get(url).response()
        val (data : ByteArray?, error) = result
        if (error != null) {
            throw error
        }
        if (data == null) {
            throw IOException("No data from download")
        }
        dest.writeBytes(data)
    }

    private fun downloadJson(url : String) : JsonElement {
        val (_,_, result) = Fuel.get(url).responseString()
        val (data : String?, error) = result
        if (error != null) {
            throw error
        }
        if (data == null) {
            throw IOException("No data from download")
        }
        return JsonParser().parse(data)
    }

    // Returns the file where the redex binary will be placed
    // and the URL where it will be downloaded from
    //
    // If we're on an unsupported platform, print to stderr and fallback
    // to finding redex on the PATH
    fun getRedexExecutableFile() : Pair<File?, String?> {
        val tag = if (requestedVersion == "latest") getLatestRedexTag()
                  else requestedVersion
        val os = getOS()
        if (os != null) {
            val redex_exec = "redex_$os"
            val url = "https://github.com/facebook/redex/releases/download/$tag/$redex_exec"
            return Pair(File("$buildDir/${redex_exec}_$tag"), url)
        }
        return Pair(null, null)
    }

    // Use the github api to find the tag name of the most recent release
    private fun getLatestRedexTag() : String {
        val releasesUrl = "https://api.github.com/repos/facebook/redex/releases"
        val releases = downloadJson(releasesUrl)

        // more recent releases are near the beginning
        val latest = releases.getAsJsonArray().get(0).getAsJsonObject()
        return latest.get("tag_name").getAsString()
    }

    // get a string like <os>_<architecture> of the current machine
    fun getOS() : String? {
        if (Os.isFamily(Os.FAMILY_UNIX)) {
            // TODO support other architectures (32bit, non x86, etc.)
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

