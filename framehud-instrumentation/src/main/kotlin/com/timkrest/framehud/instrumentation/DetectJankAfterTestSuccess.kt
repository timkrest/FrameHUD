package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.FrameHud
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Disables [DetectJankAfterTestSuccess] for a test or class. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
public annotation class SkipJankDetection

/** Resets FrameHud before each test it checks. */
public class DetectJankAfterTestSuccess @JvmOverloads constructor(
    private val thresholds: JankThresholds = JankThresholds(),
    private val onInconclusive: OnInconclusive = OnInconclusive.FAIL,
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            if (description.skipsJankDetection()) {
                base.evaluate()
                return
            }
            FrameHud.reset()
            base.evaluate()
            JankAssertions.assertNoJank(tag = description.displayName, thresholds = thresholds, onInconclusive = onInconclusive)
        }
    }

    private fun Description.skipsJankDetection(): Boolean =
        getAnnotation(SkipJankDetection::class.java) != null ||
            testClass?.isAnnotationPresent(SkipJankDetection::class.java) == true
}
