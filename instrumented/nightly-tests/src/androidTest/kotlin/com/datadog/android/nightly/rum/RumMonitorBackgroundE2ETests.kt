/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.aResourceErrorMessage
import com.datadog.android.nightly.utils.aResourceKey
import com.datadog.android.nightly.utils.aResourceMethod
import com.datadog.android.nightly.utils.anActionName
import com.datadog.android.nightly.utils.anErrorMessage
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.android.nightly.utils.exhaustiveAttributes
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measure
import com.datadog.android.nightly.utils.sendRandomActionOutcomeEvent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class RumMonitorBackgroundE2ETests {
    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun initialize(android.content.Context, com.datadog.android.core.configuration.Credentials, com.datadog.android.core.configuration.Configuration, com.datadog.android.privacy.TrackingConsent)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#constructor(Boolean, Boolean, Boolean, Boolean)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun trackBackgroundRumEvents(Boolean): Builder
     * apiMethodSignature: com.datadog.android.rum.GlobalRum#fun get(): RumMonitor
     * apiMethodSignature: com.datadog.android.rum.GlobalRum#fun isRegistered(): Boolean
     * apiMethodSignature: com.datadog.android.rum.GlobalRum#fun registerIfAbsent(RumMonitor): Boolean
     */
    @Before
    fun setUp() {
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            config = Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .trackBackgroundRumEvents(true)
                .build()
        )
    }

    // region Background Action

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun stopUserAction(RumActionType, String, Map<String, Any?> = emptyMap())
     */
    @Test
    fun rum_rummonitor_stop_background_action_with_outcome() {
        val testMethodName = "rum_rummonitor_stop_background_action_with_outcome"
        val actionName = forge.anActionName()
        val type = forge.aValueFrom(RumActionType::class.java)
        GlobalRum.get().startUserAction(
            type,
            actionName,
            attributes = defaultTestAttributes(testMethodName)
        )
        // In this case the `sendRandomActionEvent`
        // will mark the event valid to be sent, then we wait to make the event inactive and then
        // we stop it. In this moment everything is set for the event to be sent but it still needs
        // another upcoming event (start/stop view, resource, action, error) to trigger
        // the `sendAction`
        sendRandomActionOutcomeEvent(forge)
        // wait for the action to be inactive
        Thread.sleep(ACTION_INACTIVITY_THRESHOLD_MS)
        val attributes = forge.exhaustiveAttributes()
        measure(testMethodName) {
            GlobalRum.get().stopUserAction(type, actionName, attributes)
        }

        Thread.sleep(ACTION_INACTIVITY_THRESHOLD_MS)
        sendRandomActionOutcomeEvent(forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun addUserAction(RumActionType, String, Map<String, Any?>)
     */
    @Test
    fun rum_rummonitor_add_background_non_custom_action_with_no_outcome() {
        val testMethodName = "rum_rummonitor_add_background_non_custom_action_with_no_outcome"
        val actionName = forge.anActionName()
        val actionType = forge.aValueFrom(
            RumActionType::class.java,
            exclude = listOf(RumActionType.CUSTOM)
        )
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            GlobalRum.get().addUserAction(
                actionType,
                actionName,
                attributes = attributes
            )
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun addUserAction(RumActionType, String, Map<String, Any?>)
     */
    @Test
    fun rum_rummonitor_add_background_custom_action_with_outcome() {
        val testMethodName = "rum_rummonitor_add_background_custom_action_with_outcome"
        val actionName = forge.anActionName()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            GlobalRum.get().addUserAction(
                RumActionType.CUSTOM,
                actionName,
                attributes = attributes
            )
        }
        // wait for the action to be inactive
        Thread.sleep(ACTION_INACTIVITY_THRESHOLD_MS)
        // send a random action outcome event which will trigger the `sendAction` function.
        // as this is a custom action it will skip the `sideEffects` verification and it will be
        // sent immediately.
        sendRandomActionOutcomeEvent(forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun addUserAction(RumActionType, String, Map<String, Any?>)
     */
    @Test
    fun rum_rummonitor_add_background_custom_action_with_no_outcome() {
        val testMethodName = "rum_rummonitor_add_background_custom_action_with_no_outcome"
        val actionName = forge.anActionName()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            GlobalRum.get().addUserAction(
                RumActionType.CUSTOM,
                actionName,
                attributes = attributes
            )
        }
        // wait for the action to be inactive
        Thread.sleep(ACTION_INACTIVITY_THRESHOLD_MS)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun addUserAction(RumActionType, String, Map<String, Any?>)
     */
    @Test
    fun rum_rummonitor_add_background_non_custom_action_with_outcome() {
        val testMethodName = "rum_rummonitor_add_background_non_custom_action_with_outcome"
        val actionName = forge.anActionName()
        val attributes = defaultTestAttributes(testMethodName)
        val actionType = forge.aValueFrom(
            RumActionType::class.java,
            exclude = listOf(RumActionType.CUSTOM)
        )
        measure(testMethodName) {
            GlobalRum.get().addUserAction(
                actionType,
                actionName,
                attributes = attributes
            )
        }
        // send a random action outcome event which will increment the resource/error count making
        // this action event valid for being sent. Although the action event is valid it will not
        // be sent in this case because there is no other event to after to trigger the `sendAction`
        // function.
        sendRandomActionOutcomeEvent(forge)

        // wait for the action to be inactive
        Thread.sleep(ACTION_INACTIVITY_THRESHOLD_MS)
        sendRandomActionOutcomeEvent(forge)
    }

    // endregion

    // region Background Resource

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun stopResource(String, Int?, Long?, RumResourceKind, Map<String, Any?>)
     */
    @Test
    fun rum_rummonitor_stop_background_resource() {
        val testMethodName = "rum_rummonitor_stop_background_resource"
        val resourceKey = forge.aResourceKey()
        val attributes = defaultTestAttributes(testMethodName)
        GlobalRum.get().startResource(
            resourceKey,
            forge.aResourceMethod(),
            resourceKey,
            attributes = attributes
        )
        val size = forge.aLong(min = 1)
        val kind = forge.aValueFrom(RumResourceKind::class.java)
        measure(testMethodName) {
            GlobalRum.get().stopResource(
                resourceKey,
                200,
                size,
                kind,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun stopResourceWithError(String, Int?, String, RumErrorSource, Throwable, Map<String, Any?> = emptyMap())
     */
    @Test
    fun rum_rummonitor_stop_background_resource_with_error() {
        val testMethodName = "rum_rummonitor_stop_background_resource_with_error"
        val resourceKey = forge.aResourceKey()
        val method = forge.aResourceMethod()
        val attributes = defaultTestAttributes(testMethodName)
        GlobalRum.get().startResource(
            resourceKey,
            method,
            resourceKey,
            attributes = attributes
        )
        val statusCode = forge.anInt(min = 400, max = 511)
        val message = forge.aResourceErrorMessage()
        val source = forge.aValueFrom(RumErrorSource::class.java)
        val throwable = forge.aThrowable()
        measure(testMethodName) {
            GlobalRum.get().stopResourceWithError(
                resourceKey,
                statusCode,
                message,
                source,
                throwable,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun stopResourceWithError(String, Int?, String, RumErrorSource, String, String?, Map<String, Any?> = emptyMap())
     */
    @Test
    fun rum_rummonitor_stop_background_resource_with_error_stacktrace() {
        val testMethodName = "rum_rummonitor_stop_background_resource_with_error_stacktrace"
        val resourceKey = forge.aResourceKey()
        val attributes = defaultTestAttributes(testMethodName)
        GlobalRum.get().startResource(
            resourceKey,
            forge.aResourceMethod(),
            resourceKey,
            attributes = attributes
        )
        val anInt = forge.anInt(min = 400, max = 511)
        val aResourceErrorMessage = forge.aResourceErrorMessage()
        val stackTrace = forge.aString()
        val errorType = forge.aNullable { forge.anAlphabeticalString() }
        val source = forge.aValueFrom(RumErrorSource::class.java)
        measure(testMethodName) {
            GlobalRum.get().stopResourceWithError(
                resourceKey,
                anInt,
                aResourceErrorMessage,
                source,
                stackTrace,
                errorType,
                attributes
            )
        }
    }

    // endregion

    // region Background Error

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun addError(String, RumErrorSource, Throwable?, Map<String, Any?>)
     */
    @Test
    fun rum_rummonitor_add_background_error() {
        val testMethodName = "rum_rummonitor_add_background_error"
        val errorMessage = forge.anErrorMessage()
        val source = forge.aValueFrom(RumErrorSource::class.java)
        val throwable = forge.aNullable { forge.aThrowable() }
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            GlobalRum.get().addError(
                errorMessage,
                source,
                throwable,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor#fun addErrorWithStacktrace(String, RumErrorSource, String?, Map<String, Any?>)
     */
    @Test
    fun rum_rummonitor_add_background_error_with_stacktrace() {
        val testMethodName = "rum_rummonitor_add_background_error_with_stacktrace"
        val errorMessage = forge.anErrorMessage()
        val source = forge.aValueFrom(RumErrorSource::class.java)
        val stacktrace = forge.aNullable { forge.aThrowable().stackTraceToString() }
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            GlobalRum.get().addErrorWithStacktrace(
                errorMessage,
                source,
                stacktrace,
                attributes
            )
        }
    }

    // endregion
}
