package com.example.audiochannelfix

import com.example.audiochannelfix.utils.logI

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
/**
 * LSPosed module entry point.
 *
 * The fully qualified name of this class must be listed in
 * [META-INF/xposed/java_init.list].
 *
 * Lifecycle: [onModuleLoaded] → ([onSystemServerStarting] | [onPackageLoaded] → [onPackageReady])
 */
class MainHook : XposedModule() {

    override fun onPackageReady(param: PackageReadyParam) {
        //super.onPackageReady(param)
        val pkg = param.packageName
        logI("Injecting into $pkg (API ${apiVersion})")

        hookLegacyAudioTrackConstructor()
        hookAudioAttributesBuilder()
    }


    // ---- 通道一：AudioTrack(int streamType, ...) ----
    private fun hookLegacyAudioTrackConstructor() {
        // 精确匹配第一个参数为 int 的构造函数，避免误伤其他重载
        AudioTrack::class.java.declaredConstructors
            .filter { it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType }
            .forEach { ctor ->
                hook(ctor).intercept { param ->
                val streamType = param.args[0] as? Int
                if (streamType == AudioManager.STREAM_VOICE_CALL) {
                        param.args[0] = AudioManager.STREAM_MUSIC
                        logI("AudioTrack ctor: VOICE_CALL -> MUSIC")
                    }
                    param.proceed()
                }
            }
    }

    // ---- 通道二：AudioAttributes.Builder.setLegacyStreamType(int) ----
    private fun hookAudioAttributesBuilder() {
        val method = AudioAttributes.Builder::class.java
            .getDeclaredMethod("setLegacyStreamType", Int::class.javaPrimitiveType)

        hook(method).intercept { param ->
            val streamType = param.args[0] as? Int
            if (streamType == AudioManager.STREAM_VOICE_CALL) {
                param.args[0] = AudioManager.STREAM_MUSIC
                logI("setLegacyStreamType: VOICE_CALL -> MUSIC")
            }
            param.proceed()
        }
    }
}
