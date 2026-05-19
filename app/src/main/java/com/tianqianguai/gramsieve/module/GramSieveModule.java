package com.tianqianguai.gramsieve.module;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class GramSieveModule extends XposedModule {
    private static final String TELEGRAM_PACKAGE = "org.telegram.messenger";
    private static final String TAG = "GramSieve";

    private final TelegramHookInstaller hookInstaller = new TelegramHookInstaller(this);

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Module loaded by " + getFrameworkName() + " " + getFrameworkVersion());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!TELEGRAM_PACKAGE.equals(param.getPackageName()) || !param.isFirstPackage()) {
            return;
        }
        log(Log.INFO, TAG, "Telegram package loaded; waiting for app class loader");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TELEGRAM_PACKAGE.equals(param.getPackageName()) || !param.isFirstPackage()) {
            return;
        }
        try {
            hookInstaller.install(param.getClassLoader(), param.getApplicationInfo());
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Failed to install Telegram hooks", throwable);
        }
    }
}
