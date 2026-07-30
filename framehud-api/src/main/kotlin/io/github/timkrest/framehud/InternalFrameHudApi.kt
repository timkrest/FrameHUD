package io.github.timkrest.framehud

/**
 * Marks declarations that exist only so the FrameHud implementation can build model objects.
 * They are not part of the supported API and may change in any release.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This declaration is internal to FrameHud and may change without notice.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalFrameHudApi
