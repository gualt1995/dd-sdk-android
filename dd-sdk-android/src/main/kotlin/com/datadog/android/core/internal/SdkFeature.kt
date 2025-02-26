/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.persistence.NoOpPersistenceStrategy
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.advanced.CacheFileMigrator
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileHandler
import com.datadog.android.core.internal.persistence.file.batch.BatchFileOrchestrator
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.log.Logger
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("TooManyFunctions")
internal abstract class SdkFeature<T : Any, C : Configuration.Feature> {

    internal val initialized = AtomicBoolean(false)

    internal var persistenceStrategy: PersistenceStrategy<T> = NoOpPersistenceStrategy()
    internal var uploader: DataUploader = NoOpDataUploader()
    internal var uploadScheduler: UploadScheduler = NoOpUploadScheduler()
    private val featurePlugins: MutableList<DatadogPlugin> = mutableListOf()

    // region SDK Feature

    fun initialize(context: Context, configuration: C) {
        if (initialized.get()) {
            return
        }

        persistenceStrategy = createPersistenceStrategy(context, configuration)

        setupUploader(configuration)

        registerPlugins(
            configuration.plugins,
            DatadogPluginConfig(
                context = context,
                envName = CoreFeature.envName,
                serviceName = CoreFeature.serviceName,
                trackingConsent = CoreFeature.trackingConsentProvider.getConsent()
            ),
            CoreFeature.trackingConsentProvider
        )

        onInitialize(context, configuration)

        initialized.set(true)

        onPostInitialized(context)
    }

    fun isInitialized(): Boolean {
        return initialized.get()
    }

    fun clearAllData() {
        persistenceStrategy.getReader().dropAll()
    }

    fun stop() {
        if (initialized.get()) {
            unregisterPlugins()
            uploadScheduler.stopScheduling()
            persistenceStrategy = NoOpPersistenceStrategy()
            uploadScheduler = NoOpUploadScheduler()

            onStop()

            initialized.set(false)
            onPostStopped()
        }
    }

    fun getPlugins(): List<DatadogPlugin> {
        return featurePlugins
    }

    // endregion

    // region Abstract

    open fun onInitialize(context: Context, configuration: C) {}

    open fun onPostInitialized(context: Context) {}

    open fun onStop() {}

    open fun onPostStopped() {}

    abstract fun createPersistenceStrategy(
        context: Context,
        configuration: C
    ): PersistenceStrategy<T>

    abstract fun createUploader(configuration: C): DataUploader

    // endregion

    // region Internal

    private fun registerPlugins(
        plugins: List<DatadogPlugin>,
        config: DatadogPluginConfig,
        trackingConsentProvider: ConsentProvider
    ) {
        plugins.forEach {
            featurePlugins.add(it)
            it.register(config)
            trackingConsentProvider.registerCallback(it)
        }
    }

    private fun unregisterPlugins() {
        featurePlugins.forEach {
            it.unregister()
        }
        featurePlugins.clear()
    }

    private fun setupUploader(configuration: C) {
        uploadScheduler = if (CoreFeature.isMainProcess) {
            uploader = createUploader(configuration)
            DataUploadScheduler(
                persistenceStrategy.getReader(),
                uploader,
                CoreFeature.networkInfoProvider,
                CoreFeature.systemInfoProvider,
                CoreFeature.uploadFrequency,
                CoreFeature.uploadExecutorService
            )
        } else {
            NoOpUploadScheduler()
        }
        uploadScheduler.startScheduling()
    }

    /**
     * Since SDK v1.12.0, the Android SDK stores batch files in the cache directory instead
     * of the files directory. This migration ensures we don't lose any important data when
     * customers update their SDK.
     */
    protected fun migrateToCacheDir(
        context: Context,
        featureName: String,
        internalLogger: Logger
    ) {
        val fileHandler = BatchFileHandler(internalLogger)
        val config = FilePersistenceConfig()
        val migrator = CacheFileMigrator(
            fileHandler,
            CoreFeature.persistenceExecutorService,
            internalLogger
        )
        val filesDir = File(
            context.filesDir,
            FeatureFileOrchestrator.GRANTED_DIR.format(Locale.US, featureName)
        )
        val previousOrchestrator = BatchFileOrchestrator(filesDir, config, internalLogger)
        val cacheDir = File(
            context.cacheDir,
            FeatureFileOrchestrator.GRANTED_DIR.format(Locale.US, featureName)
        )
        val newOrchestrator = BatchFileOrchestrator(cacheDir, config, internalLogger)

        migrator.migrateData(null, previousOrchestrator, true, newOrchestrator)
    }

    // Used for nightly tests only
    internal fun flushStoredData() {
        persistenceStrategy.getFlusher().flush(uploader)
    }

    // endregion
}
