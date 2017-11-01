package au.com.timmutton.redexplugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException

class RedexPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project.plugins.hasPlugin(AppPlugin::class.java)) {
            val android = project.extensions.getByType(AppExtension::class.java)
            val extension = project.extensions.create("redex", RedexExtension::class.java, android)

            project.afterEvaluate {
                val download = project.tasks.create("redexDownload", RedexDownloadTask::class.java)
                download.initialise(extension)

                android.applicationVariants.all {
                    val task = project.tasks.create("redex${it.name.capitalize()}", RedexTask::class.java)
                    task.initialise(it, extension)
                    task.dependsOn(download)
                }
            }
        } else {
            throw StopExecutionException("Redex requires the android application plugin")
        }
    }
}
