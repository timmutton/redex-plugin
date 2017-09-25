package au.com.timmutton.redexplugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException

class RedexPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.extensions.create("redex", RedexPluginExtension::class.java)

        project.afterEvaluate {
            if(!project.plugins.hasPlugin(AppPlugin::class.java)) {
                throw StopExecutionException("Redex requires the android application plugin")
            }

            val android = project.extensions.getByType(AppExtension::class.java)

            val extension = project.extensions.getByType(RedexPluginExtension::class.java)
            RedexTask.configFilePath = extension.configFilePath
            RedexTask.passes = extension.passes
            RedexTask.sdkDirectory = android.sdkDirectory.toString()

            for(variant in android.applicationVariants) {
                val task = project.tasks.create("redex${variant.name.capitalize()}", RedexTask::class.java)
                task.initialise(variant)
            }
        }
	}
}
