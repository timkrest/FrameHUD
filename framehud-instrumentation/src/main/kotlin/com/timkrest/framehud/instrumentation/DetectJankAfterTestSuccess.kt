package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.FrameHud
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Leaves the annotated test or class out of [DetectJankAfterTestSuccess]. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
public annotation class SkipJankDetection

/**
 * Resets FrameHud before each test and asserts on jank once the test passes; a failing test is
 * reported as-is. Opt out of a test or class with [SkipJankDetection].
 */
public class DetectJankAfterTestSuccess(
    private val thresholds: JankThresholds = JankThresholds(),
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            if (description.skipsJankDetection()) {
                base.evaluate()
                return
            }
            FrameHud.reset()
            base.evaluate()
            JankAssertions.assertNoJank(tag = description.displayName, thresholds = thresholds)
        }
    }

    private fun Description.skipsJankDetection(): Boolean =
        getAnnotation(SkipJankDetection::class.java) != null ||
            testClass?.isAnnotationPresent(SkipJankDetection::class.java) == true
}
