package au.com.timmutton.redexplugin

/**
 * @author timmutton
 */
open class RedexPluginExtension {
    var passes: Array<String> = arrayOf("ReBindRefsPass",
                                        "BridgePass",
                                        "SynthPass",
                                        "FinalInlinePass",
                                        "DelSuperPass",
                                        "SingleImplPass",
                                        "SimpleInlinePass",
                                        "StaticReloPass",
                                        "RemoveEmptyClassesPass",
                                        "ShortenSrcStringsPass")
}