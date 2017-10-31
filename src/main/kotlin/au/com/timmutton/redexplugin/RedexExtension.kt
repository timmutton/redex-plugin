package au.com.timmutton.redexplugin

import com.android.build.gradle.AppExtension
import java.io.File

/**
 * @author timmutton
 */
open class RedexExtension(appExtension: AppExtension) {
    var configFile : File? = null
    var proguardConfigFiles : List<File>? = null
    var proguardMapFile : File? = null
    var jarFiles : List<File>? = null
    var keepFile : File? = null
    var otherArgs : String? = null
    var passes : List<String>? = null
    var showStats: Boolean = true

    val sdkDirectory: File? = appExtension.sdkDirectory
}