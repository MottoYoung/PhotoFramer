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
    fun idleStatus(): ArCoreStatus = ArCoreStatus(
        state = ArCoreRuntimeState.Idle,
        message = "ARCore 待命中"
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
                    message = "正在检查 ARCore 支持情况"
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
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
