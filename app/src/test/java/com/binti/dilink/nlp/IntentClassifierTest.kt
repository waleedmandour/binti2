package com.binti.dilink.nlp

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [IntentClassifier] — rule-based intent matching.
 *
 * Phase 1: verifies that the classifier gracefully handles inputs when
 * not fully initialized (assets unavailable in pure JVM tests) and
 * always returns a non-null IntentResult. The actual STATION/GREETINGS/
 * SOCIAL/HELP pattern matching is tested via instrumentation tests
 * on-device where the intent map asset is available.
 *
 * Note: `classifyIntent` is a suspend function (it uses withContext
 * internally), so tests run inside [runTest] to provide a coroutine scope.
 */
class IntentClassifierTest {

    @Test
    fun `classifyIntent returns UNKNOWN for unrecognized input without crashing`() = runTest {
        val classifier = IntentClassifier(android.app.Application())
        // Without initialize(), isInitialized=false. classifyIntent logs a
        // warning but still runs matchByRules() which returns null (no
        // patterns loaded), then falls through to the UNKNOWN result.
        val result = classifier.classifyIntent("أقرب محطة شحن")
        assertNotNull("Classifier must always return a result", result)
        assertEquals("UNKNOWN", result.action)
    }

    @Test
    fun `classifyIntent returns UNKNOWN for empty input`() = runTest {
        val classifier = IntentClassifier(android.app.Application())
        val result = classifier.classifyIntent("")
        assertNotNull(result)
        assertEquals("UNKNOWN", result.action)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `classifyIntent returns UNKNOWN for gibberish`() = runTest {
        val classifier = IntentClassifier(android.app.Application())
        val result = classifier.classifyIntent("xyz qwerty 12345")
        assertNotNull(result)
        assertEquals("UNKNOWN", result.action)
    }

    @Test
    fun `IntentResult data class holds all expected fields`() {
        val result = IntentResult(
            action = "STATION",
            entities = mapOf("location" to "nearby"),
            confidence = 0.9f,
            originalText = "أقرب محطة شحن",
            matchedPattern = "أَقْرَب مَحَطَّة شُحْن",
            matchedPatternResponse = "لَقِيت لَك أَقْرَب مَحَطَّة شُحْن"
        )
        assertEquals("STATION", result.action)
        assertEquals("nearby", result.entities["location"])
        assertEquals(0.9f, result.confidence)
        assertEquals("أقرب محطة شحن", result.originalText)
        assertTrue(result.matchedPatternResponse.isNotBlank())
    }

    @Test
    fun `IntentResult matchedPatternResponse defaults to empty string`() {
        val result = IntentResult(
            action = "HELP",
            entities = emptyMap(),
            confidence = 0.8f,
            originalText = "بتعملي إيه"
        )
        assertEquals("", result.matchedPatternResponse)
        assertEquals(null, result.matchedPattern)
    }
}
