package com.vulnit.debugmepls

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val DEBUG_ENABLE_JDWP = 0x1
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {

        if (lpparam?.packageName != "android") {
            return
        }

        hookResolveActivity(lpparam)
        hookGetPackageInfo(lpparam)
        hookGetApplicationInfo(lpparam)
        hookGetInstalledApplications(lpparam)
        hookProcessStart()
    }

    fun hookResolveActivity(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val profilerInfoClass =
            XposedHelpers.findClass(
                "android.app.ProfilerInfo",
                lpparam!!.classLoader
            )

        val activityTaskSupervisorClass =
            XposedHelpers.findClass(
                "com.android.server.wm.ActivityTaskSupervisor",
                lpparam.classLoader
            )

        XposedHelpers.findAndHookMethod(activityTaskSupervisorClass, "resolveActivity", Intent::class.java,
            ResolveInfo::class.java, Int::class.java, profilerInfoClass, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    val applicationInfo = (param?.args?.get(1) as ResolveInfo).activityInfo.applicationInfo
                    applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                    XposedBridge.log("[debugmepLS] Application Info: ${applicationInfo.packageName}")
                }

            })
    }

    fun hookGetPackageInfo(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val computerEngineClass =
            XposedHelpers.findClass(
                "com.android.server.pm.ComputerEngine",
                lpparam!!.classLoader
            )

        XposedHelpers.findAndHookMethod(
            computerEngineClass,
            "getPackageInfo",
            String::class.java,
            Long::class.java,
            Int::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val packageInfo = param?.result as? PackageInfo ?: return
                    val applicationInfo = packageInfo.applicationInfo ?: return
                    applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                    XposedBridge.log("[debugmepLS] Package debuggable enforced: ${packageInfo.packageName}")
                }
            }
        )
    }

    fun hookGetApplicationInfo(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val computerEngineClass =
            XposedHelpers.findClass(
                "com.android.server.pm.ComputerEngine",
                lpparam!!.classLoader
            )

        XposedHelpers.findAndHookMethod(
            computerEngineClass,
            "getApplicationInfo",
            String::class.java,
            Long::class.java,
            Int::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val applicationInfo = param?.result as? ApplicationInfo ?: return
                    applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                    XposedBridge.log("[debugmepLS] Application debuggable enforced: ${applicationInfo.packageName}")
                }
            }
        )
    }

    fun hookGetInstalledApplications(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val computerEngineClass =
            XposedHelpers.findClass(
                "com.android.server.pm.ComputerEngine",
                lpparam!!.classLoader
            )

        val parceledListSliceClass =
            XposedHelpers.findClass(
                "android.content.pm.ParceledListSlice",
                lpparam.classLoader
            )

        XposedHelpers.findAndHookMethod(
            computerEngineClass,
            "getInstalledApplications",
            Long::class.java,
            Int::class.java,
            Int::class.java,
            Boolean::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val result = param?.result ?: return
                    val applications = when {
                        result is List<*> -> result
                        parceledListSliceClass.isInstance(result) -> {
                            XposedHelpers.callMethod(result, "getList") as? List<*>
                        }
                        else -> null
                    } ?: return

                    applications.forEach {
                        val appInfo = it as? ApplicationInfo ?: return@forEach
                        appInfo.flags = appInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                        XposedBridge.log("[debugmepLS] Installed app debuggable enforced: ${appInfo.packageName}")
                    }
                }
            }
        )
    }

    fun hookProcessStart() {
        try {
            val processClass = Class.forName("android.os.Process", false, null)
            val startMethod = processClass.declaredMethods.firstOrNull { it.name == "start" }
                ?: run {
                    XposedBridge.log("[debugmepLS] Process.start not found")
                    return
                }

            XposedBridge.hookMethod(
                startMethod,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        val runtimeFlagsIndex = 5
                        val currentFlags = param?.args?.get(runtimeFlagsIndex) as? Int ?: return
                        val newFlags = currentFlags or DEBUG_ENABLE_JDWP
                        param.args[runtimeFlagsIndex] = newFlags
                        XposedBridge.log("[debugmepLS] Process.start runtimeFlags updated: $currentFlags -> $newFlags")
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[debugmepLS] Failed to hook Process.start: ${t.message}")
        }
    }

    private fun Array<Any?>.getOrNull(index: Int): Any? {
        return if (index in indices) this[index] else null
    }
}
