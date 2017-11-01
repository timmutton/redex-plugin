package au.com.timmutton.redexplugin

import java.util.regex.Pattern
import java.util.regex.Matcher
import java.nio.file.Files
import java.io.File
import de.undercouch.gradle.tasks.download.Download
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskAction

class RedexDownloadTask : DefaultTask() {
    val DEBUG = true
    val buildDir = project.buildDir.getPath()
    var tag : String? = "latest"

    private fun download(url : String, dest : String) {
        val dl = Download()
        dl.src(url)
        dl.dest(dest)
        dl.onlyIfNewer(true);
        dl.download()
    }

    // releases/latest is a webpage for the most recent release
    // of redex. Search through the html for the name of the
    // tag of this release.
    private fun getLatestRedexTag() : String {
        val latest = "https://github.com/facebook/redex/releases/latest"
        download(latest, buildDir)
        val input = File("$buildDir/latest").readText()
        val pattern = Pattern.compile(
            "/facebook/redex/releases/tag/(v\\d+\\.\\d+\\.\\d+)")
        val matcher = pattern.matcher(input)
        matcher.find()
        return matcher.group(1)
    }

    public fun initialise(ext : RedexExtension) {
        tag = ext.redexVersion
    }

    @TaskAction
    // download redex from the github release page
    public fun run() {
        if (tag == null) {
            return;
        }
        if (tag!! == "latest") {
            // defaults to latest
            tag = getLatestRedexTag()
        }
        if (Os.isFamily(Os.FAMILY_UNIX)) {
            val redex_exec = "redex_linux_x86_64"
            val linux = "https://github.com/facebook/redex/releases/download/$tag/$redex_exec"
            download(linux, buildDir);
            val redex = File("$buildDir/$redex_exec")
            redex.setExecutable(true)
            if (DEBUG) {
                println("Redex downloaded to ${redex.getAbsolutePath()}")
            }
        } else {
            println("Your platform isn't supported (yet) for downloading redex." +
                    "Please follow instructions at" +
                    "https://github.com/facebok/redex")
        }
    }
}
