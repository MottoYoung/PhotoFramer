package com.photoframer.arcore

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

enum class ArCoreRuntimeState {
    Idle,
    Checking,
    Installing,
    Ready,
    Unsupported,
    Failed
}

data class ArCoreStatus(
    val state: ArCoreRuntimeState,
    val message: String,
    val detail: String? = null
) {
    val isReady: Boolean get() = state == ArCoreRuntimeState.Ready
}

object ArCoreSupport {
    private const val PREFS_NAME = "photoframer_experiments"
    private const val KEY_ARCORE_EXPERIMENT_ENABLED = "arcore_experiment_enabled"
    private const val DEFAULT_ENABLED = false
    private const val ARCORE_PACKAGE_NAME = "com.google.ar.core"
    private const val PLAY_SERVICES_PACKAGE_NAME = "com.google.android.gms"
    private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"

    private data class RuntimeEnvironmentHint(
        val hasArCorePackage: Boolean,
        val arCoreVersionCode: Long?,
        val hasPlayServices: Boolean,
        val hasPlayStore: Boolean
    ) {
        val hasStubArCore: Boolean
            get() = hasArCorePackage && (arCoreVersionCode == null || arCoreVersionCode <= 0L)
    }

    fun idleStatus(): ArCoreStatus = ArCoreStatus(
        state = ArCoreRuntimeState.Idle,
        message = "ARCore 待命中"
    )

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ARCORE_EXPERIMENT_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ARCORE_EXPERIMENT_ENABLED, enabled)
            .apply()
    }

    fun disabledStatus(): ArCoreStatus = ArCoreStatus(
        state = ArCoreRuntimeState.Idle,
        message = "ARCore 已默认关闭",
        detail = "当前使用视觉估计 / DeviceMotion"
    )

    fun resolveForActivity(
        activity: Activity,
        requestInstall: Boolean
    ): ArCoreStatus {
        return try {
            when (ArCoreApk.getInstance().checkAvailability(activity)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreStatus(
                    state = ArCoreRuntimeState.Ready,
                    message = "ARCore 机位辅助已就绪"
                )

                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                    when (ArCoreApk.getInstance().requestInstall(activity, requestInstall)) {
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> ArCoreStatus(
                            state = ArCoreRuntimeState.Installing,
                            message = "正在请求安装 ARCore"
                        )

                        ArCoreApk.InstallStatus.INSTALLED -> ArCoreStatus(
                            state = ArCoreRuntimeState.Ready,
                            message = "ARCore 机位辅助已就绪"
                        )
                    }
                }

                ArCoreApk.Availability.UNKNOWN_CHECKING -> ArCoreStatus(
                    state = ArCoreRuntimeState.Checking,
                    message = "正在检查 ARCore 支持情况",
                    detail = buildCheckingDetail(activity)
                )

                ArCoreApk.Availability.UNKNOWN_ERROR,
                ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> ArCoreStatus(
                    state = ArCoreRuntimeState.Failed,
                    message = "ARCore 检查失败",
                    detail = "请稍后重试"
                )

                else -> ArCoreStatus(
                    state = ArCoreRuntimeState.Unsupported,
                    message = "当前设备不支持 ARCore",
                    detail = "将继续使用视觉估计"
                )
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            ArCoreStatus(
                state = ArCoreRuntimeState.Failed,
                message = "已跳过 ARCore 安装",
                detail = "当前继续使用视觉估计"
            )
        } catch (_: UnavailableDeviceNotCompatibleException) {
            ArCoreStatus(
                state = ArCoreRuntimeState.Unsupported,
                message = "当前设备不支持 ARCore",
                detail = "将继续使用视觉估计"
            )
        } catch (_: UnavailableArcoreNotInstalledException) {
            ArCoreStatus(
                state = ArCoreRuntimeState.Failed,
                message = "ARCore 尚未安装",
                detail = "请完成安装后重试"
            )
        } catch (_: UnavailableApkTooOldException) {
            ArCoreStatus(
                state = ArCoreRuntimeState.Failed,
                message = "ARCore 版本过旧",
                detail = "请更新 Google Play Services for AR"
            )
        } catch (_: UnavailableSdkTooOldException) {
            ArCoreStatus(
                state = ArCoreRuntimeState.Failed,
                message = "应用中的 ARCore SDK 版本过旧"
            )
        } catch (error: Exception) {
            ArCoreStatus(
                state = ArCoreRuntimeState.Failed,
                message = "ARCore 初始化失败",
                detail = error.message
            )
        }
    }

    fun checkingTimeoutStatus(context: Context): ArCoreStatus {
        val hint = collectEnvironmentHint(context)
        val detail = when {
            hint.hasStubArCore && !hint.hasPlayStore ->
                "系统里只有占位版 ARCore，且未检测到 Play 商店，无法自动完成安装/更新；当前继续使用视觉估计"
            hint.hasStubArCore ->
                "系统里的 ARCore 看起来是占位版，请安装或更新 Google Play Services for AR"
            !hint.hasArCorePackage && !hint.hasPlayStore ->
                "设备未安装 ARCore，且没有 Play 商店，无法自动拉起安装；当前继续使用视觉估计"
            !hint.hasPlayStore && hint.hasPlayServices ->
                "检测到 Google Play Services，但没有 Play 商店，ARCore 可能无法自动安装/更新"
            else ->
                "系统长时间未返回 ARCore 可用状态，当前继续使用视觉估计"
        }
        return ArCoreStatus(
            state = ArCoreRuntimeState.Failed,
            message = "ARCore 检查超时",
            detail = detail
        )
    }

    private fun buildCheckingDetail(context: Context): String {
        val hint = collectEnvironmentHint(context)
        return when {
            hint.hasStubArCore && !hint.hasPlayStore ->
                "检测到占位版 ARCore，且设备没有 Play 商店，系统可能无法自动激活 ARCore"
            hint.hasStubArCore ->
                "检测到占位版 ARCore，正在等待系统确认或更新为正式版本"
            !hint.hasPlayStore && hint.hasPlayServices ->
                "设备没有 Play 商店，ARCore 自动安装/更新可能失败"
            !hint.hasPlayServices ->
                "设备缺少 Google Play Services，ARCore 可能无法完成初始化"
            else ->
                "等待系统返回兼容性结果"
        }
    }

    private fun collectEnvironmentHint(context: Context): RuntimeEnvironmentHint {
        val packageManager = context.packageManager
        val arCoreInfo = runCatching {
            packageManager.getPackageInfo(ARCORE_PACKAGE_NAME, 0)
        }.getOrNull()
        val playServicesInstalled = runCatching {
            packageManager.getPackageInfo(PLAY_SERVICES_PACKAGE_NAME, 0)
        }.isSuccess
        val playStoreInstalled = runCatching {
            packageManager.getPackageInfo(PLAY_STORE_PACKAGE_NAME, 0)
        }.isSuccess

        val versionCode = arCoreInfo?.let { packageInfo ->
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
        }

        return RuntimeEnvironmentHint(
            hasArCorePackage = arCoreInfo != null,
            arCoreVersionCode = versionCode,
            hasPlayServices = playServicesInstalled,
            hasPlayStore = playStoreInstalled
        )
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
