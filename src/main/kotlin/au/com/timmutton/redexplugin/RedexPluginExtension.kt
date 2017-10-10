package au.com.timmutton.redexplugin

import java.io.File

/**
 * @author timmutton
 */
open class RedexPluginExtension {
    var configFile : File? = null
    var proguardConfigFiles : List<File>? = null
    var proguardMapFile : File? = null
    var jarFiles : List<File>? = null
    var keepFile : File? = null
    var otherArgs : String? = null
    var passes : Array<String>? = null
}
