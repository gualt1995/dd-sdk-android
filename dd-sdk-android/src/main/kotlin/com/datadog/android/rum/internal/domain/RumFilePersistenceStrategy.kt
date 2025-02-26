/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.content.Context
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileHandler
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.log.Logger
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.security.Encryption
import java.io.File
import java.util.concurrent.ExecutorService

internal class RumFilePersistenceStrategy(
    consentProvider: ConsentProvider,
    context: Context,
    eventMapper: EventMapper<Any>,
    executorService: ExecutorService,
    internalLogger: Logger,
    localDataEncryption: Encryption?,
    private val lastViewEventFile: File
) : BatchFilePersistenceStrategy<Any>(
    FeatureFileOrchestrator(
        consentProvider,
        context,
        RumFeature.RUM_FEATURE_NAME,
        executorService,
        internalLogger
    ),
    executorService,
    MapperSerializer(
        eventMapper,
        RumEventSerializer()
    ),
    PayloadDecoration.NEW_LINE_DECORATION,
    internalLogger,
    BatchFileHandler.create(internalLogger, localDataEncryption)
) {

    override fun createWriter(
        fileOrchestrator: FileOrchestrator,
        executorService: ExecutorService,
        serializer: Serializer<Any>,
        payloadDecoration: PayloadDecoration,
        internalLogger: Logger
    ): DataWriter<Any> {
        return ScheduledWriter(
            RumDataWriter(
                fileOrchestrator,
                serializer,
                payloadDecoration,
                fileHandler,
                internalLogger,
                lastViewEventFile
            ),
            executorService,
            internalLogger
        )
    }
}
