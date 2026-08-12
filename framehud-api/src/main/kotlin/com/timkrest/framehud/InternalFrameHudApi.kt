package com.timkrest.framehud

/** Unsupported API used by the FrameHud implementation. It may change in any release. */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This declaration is internal to FrameHud and may change without notice.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalFrameHudApi
