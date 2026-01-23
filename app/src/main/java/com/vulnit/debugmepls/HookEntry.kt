package com.vulnit.debugmepls

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlin.jvm.java

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val DEBUG_ENABLE_JDWP = 0x1
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {

        /*val settingsClass =
            XposedHelpers.findClass(
                "android.provider.Settings.Secure",
                lpparam!!.classLoader
            )

        XposedHelpers.findAndHookMethod(
            settingsClass,
            "getString",
            ContentResolver::class.java,
            String::class.java,
            object : XC_MethodHook() {

                override fun beforeHookedMethod(param: MethodHookParam?) {
                    super.beforeHookedMethod(param)


                    // param.args[0] -> ContentResolver
                    // param.args[1] -> String key

                    XposedBridge.log(
                        "Before original method execution: ${param!!.args[0]} || ${param.args[1]}"
                    )
                }

                override fun afterHookedMethod(param: MethodHookParam?) {
                    super.afterHookedMethod(param)

                    val key = param!!.args[1] as String

                    // We only want to hook ANDROID_ID,
                    // because getString() can be called for many keys
                    if (key == Settings.Secure.ANDROID_ID) {

                        XposedBridge.log("Original Android ID: ${param.result}")

                        // 🔥 Spoof Android ID
                        param.result = "1234567890abcdef"

                        XposedBridge.log("Fake Android ID returned")
                    }
                }
            }
        )*/
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
                    val applicationInfo = (param?.args[1] as ResolveInfo).activityInfo.applicationInfo
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
            Long::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
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
            Long::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
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
            Long::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
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
        XposedHelpers.findAndHookMethod(
            "android.os.Process",
            null,
            "start",
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            IntArray::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Array<String>::class.java,
            Array<String>::class.java,
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
    }
}
