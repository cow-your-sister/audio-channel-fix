package com.example.audiochannelfix

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import com.example.audiochannelfix.utils.logD
import com.example.audiochannelfix.utils.logI
import com.example.audiochannelfix.utils.xposedLog
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * LSPosed module entry point.
 *
 * The fully qualified name of this class must be listed in
 * [META-INF/xposed/java_init.list].
 *
 * Lifecycle: [onModuleLoaded] → ([onSystemServerStarting] | [onPackageLoaded] → [onPackageReady])
 */
class MainHook : XposedModule() {

    private companion object {
        const val TAG = "AudioChannelFix"
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        xposedLog = this
        logI("$TAG: onModuleLoaded, process=${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        logI("$TAG: onPackageReady, Injecting into ${param.packageName}")
        super.onPackageReady(param)

        hookAllAudioTrackConstructors()
        hookAudioAttributesBuilder()
        hookAudioTrackBuilder()
    }

    private fun hookAllAudioTrackConstructors() {
        val ctors = AudioTrack::class.java.declaredConstructors
        logD("AudioTrack: total ${ctors.size} constructor(s) found")

        ctors.forEachIndexed { index, ctor ->
            try {
                logD("AudioTrack: installing [$index] ${ctor.toGenericString()}")
                hook(ctor).intercept { param ->
                    dumpAudioTrackArgs(index, param.args)
                    tryRewriteAudioTrackArgs(param.args)
                    param.proceed()
                }
                logD("AudioTrack: installed [$index] OK")
            } catch (t: Throwable) {
                logD("AudioTrack: install [$index] FAILED: ${t::class.java.name}: ${t.message}", t)
            }
        }
    }

    private fun hookAudioAttributesBuilder() {
        val cls = AudioAttributes.Builder::class.java
        val intSetters = listOf("setUsage", "setLegacyStreamType")

        intSetters.forEach { name ->
            try {
                val method = cls.getDeclaredMethod(name, Int::class.javaPrimitiveType)
                logD("AudioAttributes.Builder: installing $name(int)")
                hook(method).intercept { param ->
                    val value = param.args.getOrNull(0) as? Int
                    logD("AudioAttributes.Builder.$name invoked: value=$value")

                    if (value != null) {
                        val replacement = when (name) {
                            "setUsage" -> if (
                                value == AudioAttributes.USAGE_VOICE_COMMUNICATION
                            ) {
                                AudioAttributes.USAGE_MEDIA
                            } else {
                                value
                            }
                            "setLegacyStreamType" -> if (value == AudioManager.STREAM_VOICE_CALL) {
                                AudioManager.STREAM_MUSIC
                            } else {
                                value
                            }
                            else -> value
                        }
                        if (replacement != value) {
                            param.args[0] = replacement
                            logD("AudioAttributes.Builder.$name: rewritten $value -> $replacement")
                        }
                    }
                    param.proceed()
                }
                logD("AudioAttributes.Builder: installed $name OK")
            } catch (t: Throwable) {
                logD(
                    "AudioAttributes.Builder: install $name FAILED: ${t::class.java.name}: ${t.message}",
                    t
                )
            }
        }

        try {
            val method = cls.getDeclaredMethod("build")
            logD("AudioAttributes.Builder: installing build()")
            hook(method).intercept { param ->
                val result = param.proceed() as? AudioAttributes
                if (result != null) {
                    logD(
                        "AudioAttributes.Builder.build() -> usage=${result.usage}, " +
                            "contentType=${result.contentType}, flags=${result.flags}"
                    )
                }
            }
            logD("AudioAttributes.Builder: installed build() OK")
        } catch (t: Throwable) {
            logD(
                "AudioAttributes.Builder: install build() FAILED: ${t::class.java.name}: ${t.message}",
                t
            )
        }
    }

    private fun hookAudioTrackBuilder() {
        try {
            val cls = AudioTrack.Builder::class.java
            val method = cls.getDeclaredMethod("setAudioAttributes", AudioAttributes::class.java)
            logD("AudioTrack.Builder: installing setAudioAttributes")
            hook(method).intercept { param ->
                val attr = param.args.getOrNull(0) as? AudioAttributes
                logD(
                    "AudioTrack.Builder.setAudioAttributes: usage=${attr?.usage}, " +
                        "contentType=${attr?.contentType}, flags=${attr?.flags}"
                )
                if (attr != null) {
                    val replacement = rewriteAudioAttributes(attr)
                    if (replacement !== attr) {
                        param.args[0] = replacement
                    }
                }
                param.proceed()
            }
            logD("AudioTrack.Builder: installed setAudioAttributes OK")
        } catch (t: Throwable) {
            logD(
                "AudioTrack.Builder: install setAudioAttributes FAILED: " +
                    "${t::class.java.name}: ${t.message}",
                t
            )
        }
    }

    private fun dumpAudioTrackArgs(index: Int, args: MutableList<Any?>) {
        val description = buildString {
            append("AudioTrack ctor[$index] args: ")
            args.forEachIndexed { argumentIndex, argument ->
                append("[$argumentIndex]=")
                when (argument) {
                    null -> append("null")
                    is AudioAttributes -> append(
                        "AudioAttributes(usage=${argument.usage}, " +
                            "contentType=${argument.contentType}, flags=${argument.flags})"
                    )
                    is ByteArray -> append("byte[${argument.size}]")
                    is IntArray -> append("int[${argument.size}]")
                    else -> append("${argument::class.java.simpleName}($argument)")
                }
                append("; ")
            }
        }
        logD(description)
    }

    private fun tryRewriteAudioTrackArgs(args: MutableList<Any?>) {
        when (val first = args.getOrNull(0)) {
            is Int -> if (first == AudioManager.STREAM_VOICE_CALL) {
                args[0] = AudioManager.STREAM_MUSIC
                logD("AudioTrack arg[0]: STREAM_VOICE_CALL -> STREAM_MUSIC")
            }
            is AudioAttributes -> {
                val replacement = rewriteAudioAttributes(first)
                if (replacement !== first) {
                    args[0] = replacement
                }
            }
        }
    }

    private fun rewriteAudioAttributes(attr: AudioAttributes): AudioAttributes {
        val usage = attr.usage
        if (
            usage != AudioAttributes.USAGE_VOICE_COMMUNICATION
        ) {
            return attr
        }

        return try {
            val replacement = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(attr.contentType)
                .setFlags(attr.flags)
                .build()
            logD("AudioAttributes rewritten: usage $usage -> USAGE_MEDIA")
            replacement
        } catch (t: Throwable) {
            logD("AudioAttributes rebuild FAILED: ${t::class.java.name}: ${t.message}", t)
            attr
        }
    }
}
