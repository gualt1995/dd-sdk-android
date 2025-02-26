/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.log

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.datadog.android.sdk.ActivityProfiling
import com.datadog.android.sdk.rules.AbstractProfilingRule
import com.datadog.android.sdk.rules.CPUProfilingRule
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.tools.unit.forge.ThrowableForgeryFactory
import fr.xgouchet.elmyr.junit4.ForgeRule
import java.util.concurrent.TimeUnit
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
internal class CPUProfileForLogs {

    @get:Rule
    val mockServerRule = MockServerActivityTestRule(ActivityProfiling::class.java)

    @get:Rule
    val cpuProfilingRule = CPUProfilingRule()

    @get:Rule
    val forge = ForgeRule().withFactory(ThrowableForgeryFactory())

    @Test
    @Ignore("Not ran on CI, run locally whenever we build a new release.")
    fun profileLoggingWithAdditionalAttributes() {
        val attributes = mutableMapOf<String, String>()
        for (i in 0..100) {
            attributes[forge.anAlphabeticalString()] = forge.anHexadecimalString()
        }

        cpuProfilingRule.profile(
            runThreshold = 15.0, // allow up to 15% usage above normal
            runConfig = AbstractProfilingRule.ProfilingConfig(
                500,
                TimeUnit.SECONDS.toMillis(30),
                TimeUnit.SECONDS.toMillis(1)
            ),
            warmupConfig = AbstractProfilingRule.ProfilingConfig(
                1,
                TimeUnit.SECONDS.toMillis(1),
                TimeUnit.SECONDS.toMillis(1)
            )
        ) {
            mockServerRule.activity.logger.d("Test Crash", null, attributes)
        }
    }

    @Test
    @Ignore("Not ran on CI, run locally whenever we build a new release.")
    fun profileLoggingWithThrowable() {
        cpuProfilingRule.profile(
            runThreshold = 15.0, // allow up to 15% usage above normal
            runConfig = AbstractProfilingRule.ProfilingConfig(
                500,
                TimeUnit.SECONDS.toMillis(30),
                TimeUnit.SECONDS.toMillis(1)
            ),
            warmupConfig = AbstractProfilingRule.ProfilingConfig(
                1,
                TimeUnit.SECONDS.toMillis(1),
                TimeUnit.SECONDS.toMillis(1)
            )
        ) {
            val throwable = forge.getForgery<Throwable>()
            mockServerRule.activity.logger.d("Test Crash", throwable)
        }
    }
}
