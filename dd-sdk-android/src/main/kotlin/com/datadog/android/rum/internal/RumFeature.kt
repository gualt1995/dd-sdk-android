/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.debug.UiRumDebugListener
import com.datadog.android.rum.internal.domain.RumFilePersistenceStrategy
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.rum.internal.net.RumOkHttpUploaderV2
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.vitals.AggregatingVitalMonitor
import com.datadog.android.rum.internal.vitals.CPUVitalReader
import com.datadog.android.rum.internal.vitals.MemoryVitalReader
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalFrameCallback
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.internal.vitals.VitalObserver
import com.datadog.android.rum.internal.vitals.VitalReader
import com.datadog.android.rum.internal.vitals.VitalReaderRunnable
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object RumFeature : SdkFeature<Any, Configuration.Feature.RUM>() {

    internal const val RUM_FEATURE_NAME = "rum"

    internal var samplingRate: Float = 0f
    internal var backgroundEventTracking: Boolean = false

    internal var viewTrackingStrategy: ViewTrackingStrategy = NoOpViewTrackingStrategy()
    internal var actionTrackingStrategy: UserActionTrackingStrategy =
        NoOpUserActionTrackingStrategy()
    internal var rumEventMapper: EventMapper<Any> = NoOpEventMapper()
    internal var longTaskTrackingStrategy: TrackingStrategy = NoOpTrackingStrategy()

    internal var cpuVitalMonitor: VitalMonitor = NoOpVitalMonitor()
    internal var memoryVitalMonitor: VitalMonitor = NoOpVitalMonitor()
    internal var frameRateVitalMonitor: VitalMonitor = NoOpVitalMonitor()

    internal var debugActivityLifecycleListener: Application.ActivityLifecycleCallbacks? = null

    internal lateinit var vitalExecutorService: ScheduledThreadPoolExecutor
    internal lateinit var anrDetectorExecutorService: ExecutorService
    internal lateinit var anrDetectorRunnable: ANRDetectorRunnable
    internal lateinit var anrDetectorHandler: Handler
    internal lateinit var appContext: Context

    // region SdkFeature

    override fun onInitialize(context: Context, configuration: Configuration.Feature.RUM) {
        samplingRate = configuration.samplingRate
        backgroundEventTracking = configuration.backgroundEventTracking
        rumEventMapper = configuration.rumEventMapper

        configuration.viewTrackingStrategy?.let { viewTrackingStrategy = it }
        configuration.userActionTrackingStrategy?.let { actionTrackingStrategy = it }
        configuration.longTaskTrackingStrategy?.let { longTaskTrackingStrategy = it }

        initializeVitalMonitors()
        initializeANRDetector()

        registerTrackingStrategies(context)

        appContext = context.applicationContext
    }

    override fun onStop() {
        unregisterTrackingStrategies(CoreFeature.contextRef.get())

        viewTrackingStrategy = NoOpViewTrackingStrategy()
        actionTrackingStrategy = NoOpUserActionTrackingStrategy()
        longTaskTrackingStrategy = NoOpTrackingStrategy()
        rumEventMapper = NoOpEventMapper()

        cpuVitalMonitor = NoOpVitalMonitor()
        memoryVitalMonitor = NoOpVitalMonitor()
        frameRateVitalMonitor = NoOpVitalMonitor()

        vitalExecutorService.shutdownNow()
        anrDetectorExecutorService.shutdownNow()
        anrDetectorRunnable.stop()
    }

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.RUM
    ): PersistenceStrategy<Any> {
        return RumFilePersistenceStrategy(
            CoreFeature.trackingConsentProvider,
            context,
            configuration.rumEventMapper,
            CoreFeature.persistenceExecutorService,
            sdkLogger,
            CoreFeature.localDataEncryption,
            DatadogNdkCrashHandler.getLastViewEventFile(context)
        )
    }

    override fun createUploader(configuration: Configuration.Feature.RUM): DataUploader {
        return RumOkHttpUploaderV2(
            configuration.endpointUrl,
            CoreFeature.clientToken,
            CoreFeature.sourceName,
            CoreFeature.sdkVersion,
            CoreFeature.okHttpClient
        )
    }

    override fun onPostInitialized(context: Context) {
        migrateToCacheDir(context, RUM_FEATURE_NAME, sdkLogger)
    }

    // endregion

    // region Internal

    internal fun enableDebugging() {
        val context = appContext
        if (context is Application) {
            debugActivityLifecycleListener = UiRumDebugListener()
            context.registerActivityLifecycleCallbacks(debugActivityLifecycleListener)
        }
    }

    internal fun disableDebugging() {
        val context = appContext
        if (debugActivityLifecycleListener != null && context is Application) {
            context.unregisterActivityLifecycleCallbacks(debugActivityLifecycleListener)
            debugActivityLifecycleListener = null
        }
    }

    private fun registerTrackingStrategies(appContext: Context) {
        actionTrackingStrategy.register(appContext)
        viewTrackingStrategy.register(appContext)
        longTaskTrackingStrategy.register(appContext)
    }

    private fun unregisterTrackingStrategies(appContext: Context?) {
        actionTrackingStrategy.unregister(appContext)
        viewTrackingStrategy.unregister(appContext)
        longTaskTrackingStrategy.unregister(appContext)
    }

    private fun initializeVitalMonitors() {
        cpuVitalMonitor = AggregatingVitalMonitor()
        memoryVitalMonitor = AggregatingVitalMonitor()
        frameRateVitalMonitor = AggregatingVitalMonitor()

        @Suppress("UnsafeThirdPartyFunctionCall") // pool size can't be <= 0
        vitalExecutorService = ScheduledThreadPoolExecutor(1)

        initializeVitalMonitor(CPUVitalReader(), cpuVitalMonitor)
        initializeVitalMonitor(MemoryVitalReader(), memoryVitalMonitor)

        val vitalFrameCallback = VitalFrameCallback(frameRateVitalMonitor) { isInitialized() }
        try {
            Choreographer.getInstance().postFrameCallback(vitalFrameCallback)
        } catch (e: IllegalStateException) {
            // This can happen if the SDK is initialized on a Thread with no looper
            sdkLogger.e("Unable to initialize the Choreographer FrameCallback", e)
            devLogger.w(
                "It seems you initialized the SDK on a thread without a Looper: " +
                    "we won't be able to track your Views' refresh rate."
            )
        }
    }

    private fun initializeVitalMonitor(
        vitalReader: VitalReader,
        vitalObserver: VitalObserver
    ) {
        val readerRunnable = VitalReaderRunnable(
            vitalReader,
            vitalObserver,
            vitalExecutorService,
            VITAL_UPDATE_PERIOD_MS
        )
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            vitalExecutorService.schedule(
                readerRunnable,
                VITAL_UPDATE_PERIOD_MS,
                TimeUnit.MILLISECONDS
            )
        } catch (e: RejectedExecutionException) {
            sdkLogger.e(ERROR_VITAL_TASK_REJECTED, e)
        }
    }

    private fun initializeANRDetector() {
        anrDetectorHandler = Handler(Looper.getMainLooper())
        anrDetectorRunnable = ANRDetectorRunnable(anrDetectorHandler)
        anrDetectorExecutorService = Executors.newSingleThreadExecutor()
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            anrDetectorExecutorService.execute(anrDetectorRunnable)
        } catch (e: RejectedExecutionException) {
            sdkLogger.e(ERROR_ANR_TASK_REJECTED, e)
        }
    }

    // Update Vitals every second
    private const val VITAL_UPDATE_PERIOD_MS = 100L

    const val ERROR_ANR_TASK_REJECTED = "Unable to schedule ANR detection task on the executor"
    const val ERROR_VITAL_TASK_REJECTED =
        "Unable to schedule Vitals monitoring task on the executor"
    // endregion
}
