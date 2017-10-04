package au.com.timmutton.redexplugin

/**
 * @author timmutton
 */
open class RedexPluginExtension {
    var configFile : String? = null
    var proguardConfigFiles : Array<String>? = null
    var proguardMapFile : String? = null
    var jarFiles : Array<String>? = null
    var keepFile : String? = null
    var otherArgs : String? = null
    var passes : Array<String>? = null
}
