package au.com.timmutton.redexplugin

import com.android.build.gradle.AppExtension
import java.io.File

/**
 * @author timmutton
 */
open class RedexExtension(appExtension: AppExtension) {
    var configFile : String? = null
    var proguardConfigFiles : List<String>? = null
    var proguardMapFile : String? = null
    var jarFiles : List<String>? = null
    var keepFile : String? = null
    var otherArgs : String? = null
    var passes : List<String>? = null
    var showStats: Boolean = true

    val sdkDirectory: File? = appExtension.sdkDirectory

    // null means don't download
    // latest means the most recent redex release
    // any other string is a tag name in
    // github.com/facebook/redex/releases/tag/<tag>
    val redexVersion : String? = "latest"
}
