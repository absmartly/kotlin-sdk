package com.absmartly.sdk

/** SDK identity sent in the X-Agent header and optional system attributes. */
object SdkVersion {
    const val NAME = "absmartly-kotlin-sdk"
    const val VERSION = "1.0.0"
    const val AGENT = "$NAME/$VERSION"
}
