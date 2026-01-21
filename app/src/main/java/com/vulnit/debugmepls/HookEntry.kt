package com.vulnit.debugmepls

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ResolveInfo
import android.provider.Settings
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlin.jvm.java

class HookEntry : IXposedHookLoadPackage {

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
        hookResolveActivity(lpparam);
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
                    XposedBridge.log("[debugmepLS]Application Info: $applicationInfo")
                }

            })
    }
}