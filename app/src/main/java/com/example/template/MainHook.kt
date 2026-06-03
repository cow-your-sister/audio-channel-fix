package com.example.template

import com.example.template.utils.xposedLog
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
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

    /**
     * Called once when this module is loaded into a process.
     *
     * [XposedModule] implements [XposedInterface]; storing `this` here allows
     * the [com.example.template.utils] logging helpers to reach the framework
     * logger without importing [XposedInterface] everywhere.
     *
     * @param param process-level loading information
     */
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        xposedLog = this
    }

    /**
     * Called once when `system_server` boots.
     *
     * Place hooks that target Android framework services here.
     * Use [param.getClassLoader][SystemServerStartingParam.getClassLoader]
     * to obtain the `system_server` classloader.
     *
     * @param param system-server boot information and classloader
     */
    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)
    }

    /**
     * Called once per package after the default classloader is ready,
     * but before [AppComponentFactory] is instantiated.
     *
     * This fires earlier than [onPackageReady]. Use this only when you need
     * to hook [AppComponentFactory] itself, or intercept something before the
     * app's custom classloader takes over. For most app hooks, prefer
     * [onPackageReady] — the full classloader is available there.
     *
     * Note: in `system_server`, [onSystemServerStarting] replaces the first
     * invocation, so [PackageLoadedParam.isFirstPackage] never returns
     * `true` here.
     *
     * @param param information about the package being loaded, including
     *              [package name][PackageLoadedParam.getPackageName],
     *              [default classloader][PackageLoadedParam.getDefaultClassLoader],
     *              and [whether this is the first process][PackageLoadedParam.isFirstPackage]
     */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
    }

    /**
     * Called each time a process of a scoped application starts.
     *
     * Place hooks that target app code here.
     *
     * @param param information about the package being loaded, including
     *              [package name][PackageReadyParam.getPackageName],
     *              [classloader][PackageReadyParam.getDefaultClassLoader], and
     *              [whether this is the first process][PackageReadyParam.isFirstPackage]
     */
    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
    }

}
