package com.timkrest.framehud.internal

import com.timkrest.framehud.BaselineEnvironment

internal fun JsonObjectScope.putEnvironment(environment: BaselineEnvironment) {
    put(MANUFACTURER, environment.manufacturer)
    put(MODEL, environment.model)
    put(API_LEVEL, environment.apiLevel)
}

internal fun JsonValue.environment(): BaselineEnvironment? = readOrNull {
    BaselineEnvironment(
        manufacturer = string(MANUFACTURER) ?: return@readOrNull null,
        model = string(MODEL) ?: return@readOrNull null,
        apiLevel = int(API_LEVEL) ?: return@readOrNull null,
    )
}

private const val MANUFACTURER = "manufacturer"
private const val MODEL = "model"
private const val API_LEVEL = "apiLevel"
