package com.vulnit.debugmepls

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Method

private lateinit var module: HookEntry

class HookEntry(base: XposedInterface, param: ModuleLoadedParam) : XposedModule(base, param) {

    companion object {
        private const val DEBUG_ENABLE_JDWP = 0x1
    }

    init {
        module = this
    }

    override fun onSystemServerLoaded(param: XposedModuleInterface.SystemServerLoadedParam) {
        super.onSystemServerLoaded(param)

        val classLoader = param.classLoader
        hookResolveActivity(classLoader)
        hookGetPackageInfo(classLoader)
        hookGetApplicationInfo(classLoader)
        hookGetInstalledApplications(classLoader)
        hookProcessStart()
    }

    @XposedHooker
    class ResolveActivityHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @BeforeInvocation
            fun beforeInvocation(callback: BeforeHookCallback) {
                val resolveInfo = callback.args.getOrNull(1) as? ResolveInfo ?: return
                val applicationInfo = resolveInfo.activityInfo.applicationInfo
                if (!module.isPackageEnabled(applicationInfo.packageName)) {
                    return
                }
                applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                module.log("[debugmepLS] Application Info: ${applicationInfo.packageName}")
            }
        }
    }

    @XposedHooker
    class PackageInfoHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @AfterInvocation
            fun afterInvocation(callback: AfterHookCallback) {
                val packageInfo = callback.result as? PackageInfo ?: return
                val applicationInfo = packageInfo.applicationInfo ?: return
                if (!module.isPackageEnabled(applicationInfo.packageName)) {
                    return
                }
                applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                module.log("[debugmepLS] Package debuggable enforced: ${packageInfo.packageName}")
            }
        }
    }

    @XposedHooker
    class ApplicationInfoHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @AfterInvocation
            fun afterInvocation(callback: AfterHookCallback) {
                val applicationInfo = callback.result as? ApplicationInfo ?: return
                if (!module.isPackageEnabled(applicationInfo.packageName)) {
                    return
                }
                applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                module.log("[debugmepLS] Application debuggable enforced: ${applicationInfo.packageName}")
            }
        }
    }

    @XposedHooker
    class InstalledApplicationsHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @AfterInvocation
            fun afterInvocation(callback: AfterHookCallback) {
                val result = callback.result ?: return
                val applications = when {
                    result is List<*> -> result
                    result.javaClass.name == "android.content.pm.ParceledListSlice" -> {
                        runCatching {
                            result.javaClass.getMethod("getList").invoke(result) as? List<*>
                        }.getOrNull()
                    }
                    else -> null
                } ?: return

                applications.forEach {
                    val appInfo = it as? ApplicationInfo ?: return@forEach
                    if (!module.isPackageEnabled(appInfo.packageName)) {
                        return@forEach
                    }
                    appInfo.flags = appInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                    module.log("[debugmepLS] Installed app debuggable enforced: ${appInfo.packageName}")
                }
            }
        }
    }

    @XposedHooker
    class ProcessStartHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @BeforeInvocation
            fun beforeInvocation(callback: BeforeHookCallback) {
                val runtimeFlagsIndex = 5
                val args = callback.args
                if (args.size <= runtimeFlagsIndex) {
                    return
                }
                if (!module.isProcessSelected(args)) {
                    return
                }
                val currentFlags = args[runtimeFlagsIndex] as? Int ?: return
                val newFlags = currentFlags or DEBUG_ENABLE_JDWP
                args[runtimeFlagsIndex] = newFlags
                module.log("[debugmepLS] Process.start runtimeFlags updated: $currentFlags -> $newFlags")
            }
        }
    }

    private fun hookResolveActivity(classLoader: ClassLoader) {
        val profilerInfoClass = classLoader.loadClass("android.app.ProfilerInfo")
        val activityTaskSupervisorClass =
            classLoader.loadClass("com.android.server.wm.ActivityTaskSupervisor")
        val method = findMethod(
            activityTaskSupervisorClass,
            "resolveActivity",
            Intent::class.java,
            ResolveInfo::class.java,
            Int::class.java,
            profilerInfoClass
        )
        if (method == null) {
            log("[debugmepLS] resolveActivity not found")
            return
        }
        hook(method, ResolveActivityHooker::class.java)
    }

    private fun hookGetPackageInfo(classLoader: ClassLoader) {
        val computerEngineClass = classLoader.loadClass("com.android.server.pm.ComputerEngine")
        val method = findMethod(
            computerEngineClass,
            "getPackageInfo",
            String::class.java,
            Long::class.java,
            Int::class.java
        )
        if (method == null) {
            log("[debugmepLS] getPackageInfo not found")
            return
        }
        hook(method, PackageInfoHooker::class.java)
    }

    private fun hookGetApplicationInfo(classLoader: ClassLoader) {
        val computerEngineClass = classLoader.loadClass("com.android.server.pm.ComputerEngine")
        val method = findMethod(
            computerEngineClass,
            "getApplicationInfo",
            String::class.java,
            Long::class.java,
            Int::class.java
        )
        if (method == null) {
            log("[debugmepLS] getApplicationInfo not found")
            return
        }
        hook(method, ApplicationInfoHooker::class.java)
    }

    private fun hookGetInstalledApplications(classLoader: ClassLoader) {
        val computerEngineClass = classLoader.loadClass("com.android.server.pm.ComputerEngine")
        val method = findMethod(
            computerEngineClass,
            "getInstalledApplications",
            Long::class.java,
            Int::class.java,
            Int::class.java,
            Boolean::class.java
        )
        if (method == null) {
            log("[debugmepLS] getInstalledApplications not found")
            return
        }
        hook(method, InstalledApplicationsHooker::class.java)
    }

    private fun hookProcessStart() {
        try {
            val processClass = Class.forName("android.os.Process", false, null)
            val startMethod = processClass.declaredMethods.firstOrNull { it.name == "start" }
                ?: run {
                    log("[debugmepLS] Process.start not found")
                    return
                }
            hook(startMethod, ProcessStartHooker::class.java)
        } catch (t: Throwable) {
            log("[debugmepLS] Failed to hook Process.start: ${t.message}")
        }
    }

    private val prefs by lazy { getRemotePreferences(DebugConfig.PREFS_NAME) }

    private fun isPackageEnabled(packageName: String): Boolean {
        val enabledSet =
            prefs.getStringSet(DebugConfig.KEY_ENABLED_PACKAGES, emptySet()) ?: emptySet()
        return enabledSet.contains(packageName)
    }

    private fun isProcessSelected(args: Array<Any?>): Boolean {
        val enabled =
            prefs.getStringSet(DebugConfig.KEY_ENABLED_PACKAGES, emptySet()) ?: emptySet()
        if (enabled.isEmpty()) {
            return false
        }
        for (arg in args) {
            val raw = arg as? String ?: continue
            val pkg = raw.substringBefore(':')
            if (enabled.contains(pkg)) {
                return true
            }
        }
        return false
    }

    private fun findMethod(
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>
    ): Method? {
        return try {
            clazz.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            try {
                clazz.getMethod(name, *parameterTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                null
            }
        }
    }
}
