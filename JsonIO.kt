package com.gufont.app.data.json

import kotlinx.serialization.json.Json

object JsonIO {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
}
