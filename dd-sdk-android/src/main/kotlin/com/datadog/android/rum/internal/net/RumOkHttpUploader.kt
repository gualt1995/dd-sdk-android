/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.rum.RumAttributes
import java.util.Locale
import okhttp3.Call

internal open class RumOkHttpUploader(
    endpoint: String,
    token: String,
    callFactory: Call.Factory,
    androidInfoProvider: AndroidInfoProvider
) : DataOkHttpUploader(
    buildUrl(endpoint, token),
    callFactory,
    androidInfoProvider,
    CONTENT_TYPE_TEXT_UTF8
) {

    private val tags: String by lazy {
        val elements = mutableListOf(
            "${RumAttributes.SERVICE_NAME}:${CoreFeature.serviceName}",
            "${RumAttributes.APPLICATION_VERSION}:${CoreFeature.packageVersion}",
            "${RumAttributes.SDK_VERSION}:${BuildConfig.SDK_VERSION_NAME}",
            "${RumAttributes.ENV}:${CoreFeature.envName}"
        )

        if (CoreFeature.variant.isNotEmpty()) {
            elements.add("${RumAttributes.VARIANT}:${CoreFeature.variant}")
        }

        elements.joinToString(",")
    }

    // region DataOkHttpUploader

    override fun buildQueryParams(): MutableMap<String, Any> {
        return mutableMapOf(
            QP_SOURCE to CoreFeature.sourceName,
            QP_TAGS to tags
        )
    }

    // endregion

    companion object {
        internal const val QP_TAGS = "ddtags"
        internal const val UPLOAD_URL =
            "%s/v1/input/%s"

        private fun buildUrl(endpoint: String, token: String): String {
            return String.format(
                Locale.US,
                UPLOAD_URL,
                endpoint,
                token
            )
        }
    }
}
