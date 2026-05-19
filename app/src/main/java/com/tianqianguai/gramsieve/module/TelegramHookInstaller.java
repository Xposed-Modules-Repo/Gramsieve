package com.tianqianguai.gramsieve.module;

import android.content.pm.ApplicationInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.tianqianguai.gramsieve.R;
import com.tianqianguai.gramsieve.config.ModuleConfigStore;
import com.tianqianguai.gramsieve.config.XposedConfigProvider;
import com.tianqianguai.gramsieve.core.FilterConfig;
import com.tianqianguai.gramsieve.core.FilterDecision;
import com.tianqianguai.gramsieve.core.FilterEngine;
import com.tianqianguai.gramsieve.core.MessageSnapshot;
import com.tianqianguai.gramsieve.ui.ConfigDialogActivity;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class TelegramHookInstaller {
    private static final String TAG = "GramSieve";
    private static final String MODULE_PACKAGE = "com.tianqianguai.gramsieve";
    private static final int MENU_ID_CHAT = 0x47530011;
    private static final int MENU_ID_GLOBAL = 0x47530012;

    private final XposedModule module;
    private XposedConfigProvider configProvider;
    private final FilterEngine filterEngine = new FilterEngine();
    private final DecisionCache decisionCache = new DecisionCache();
    private final AtomicInteger bindingProbeBudget = new AtomicInteger(12);
    private final AtomicInteger hookEntryBudget = new AtomicInteger(24);
    private final AtomicInteger decisionProbeBudget = new AtomicInteger(12);
    private boolean installed;
    private Resources moduleResources;

    TelegramHookInstaller(XposedModule module) {
        this.module = module;
    }

    synchronized void install(ClassLoader classLoader, ApplicationInfo applicationInfo) {
        if (installed) {
            return;
        }
        if (configProvider == null) {
            configProvider = new XposedConfigProvider(
                    MODULE_PACKAGE,
                    () -> module.getRemotePreferences(ModuleConfigStore.PREFS_NAME)
            );
        }
        logRemoteCapabilities();
        logTelegramVersion(classLoader, applicationInfo);
        hookChatMessageCell(classLoader);
        hookRecyclerViewBinding(classLoader);
        hookChatActivityAdapter(classLoader);
        hookChatActivityMenu(classLoader);
        hookSettingsActivityMenu(classLoader);
        hookProfileSettingsMenu(classLoader);
        installed = true;
        info("Installed Telegram hooks");
    }

    private void logRemoteCapabilities() {
        try {
            String[] remoteFiles = module.listRemoteFiles();
            info(
                    "Remote caps properties=" + module.getFrameworkProperties()
                            + " files=" + (remoteFiles == null ? "null" : Arrays.toString(remoteFiles))
            );
        } catch (Throwable throwable) {
            error("Remote capability probe failed", throwable);
        }
    }

    private void hookChatMessageCell(ClassLoader classLoader) {
        try {
            Class<?> messageObjectClass = classLoader.loadClass("org.telegram.messenger.MessageObject");
            Class<?> groupedMessagesClass = classLoader.loadClass("org.telegram.messenger.MessageObject$GroupedMessages");
            Class<?> cellClass = classLoader.loadClass("org.telegram.ui.Cells.ChatMessageCell");
            boolean hooked = false;
            hooked |= tryHookMessageMethod(
                    cellClass,
                    "setMessageObject",
                    new Class<?>[]{messageObjectClass, groupedMessagesClass, boolean.class, boolean.class}
            );
            hooked |= tryHookMessageMethod(
                    cellClass,
                    "setMessageObject",
                    new Class<?>[]{messageObjectClass, groupedMessagesClass, boolean.class, boolean.class, boolean.class}
            );
            hooked |= tryHookMessageMethod(
                    cellClass,
                    "setMessageObject",
                    new Class<?>[]{messageObjectClass, groupedMessagesClass, boolean.class, boolean.class, boolean.class, boolean.class}
            );
            hooked |= tryHookMessageMethod(
                    cellClass,
                    "setMessageObjectInternal",
                    new Class<?>[]{messageObjectClass}
            );
            hooked |= tryHookMessageMethod(
                    cellClass,
                    "setMessageContent",
                    new Class<?>[]{messageObjectClass, groupedMessagesClass, boolean.class, boolean.class, boolean.class, boolean.class}
            );
            hooked |= tryHookCellLifecycleMethod(
                    cellClass,
                    "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class}
            );
            hooked |= tryHookCellLifecycleMethod(
                    cellClass,
                    "onAttachedToWindow",
                    new Class<?>[0]
            );
            if (!hooked) {
                throw new IllegalStateException("No ChatMessageCell hook points were registered");
            }
        } catch (Throwable throwable) {
            error("Failed to hook ChatMessageCell", throwable);
        }
    }

    private boolean tryHookMessageMethod(Class<?> cellClass, String methodName, Class<?>[] parameterTypes) {
        String signature = methodName + signatureOf(parameterTypes);
        try {
            Method method = Reflect.method(cellClass, methodName, parameterTypes);
            deoptimize(method, "ChatMessageCell." + signature);
            hook(method, this::handleMessageBinding);
            info("Hooked ChatMessageCell." + signature);
            return true;
        } catch (NoSuchMethodException ignored) {
            info("ChatMessageCell." + signature + " not present in this Telegram build");
            return false;
        } catch (Throwable throwable) {
            error("Failed to hook ChatMessageCell." + signature, throwable);
            return false;
        }
    }

    private boolean tryHookCellLifecycleMethod(Class<?> cellClass, String methodName, Class<?>[] parameterTypes) {
        String signature = methodName + signatureOf(parameterTypes);
        try {
            Method method = Reflect.method(cellClass, methodName, parameterTypes);
            deoptimize(method, "ChatMessageCell." + signature);
            hook(method, this::handleCellLifecycle);
            info("Hooked ChatMessageCell." + signature);
            return true;
        } catch (NoSuchMethodException ignored) {
            info("ChatMessageCell." + signature + " not present in this Telegram build");
            return false;
        } catch (Throwable throwable) {
            error("Failed to hook ChatMessageCell." + signature, throwable);
            return false;
        }
    }

    private static String signatureOf(Class<?>[] parameterTypes) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Class<?> parameterType = parameterTypes[i];
            builder.append(parameterType == null ? "null" : parameterType.getSimpleName());
        }
        return builder.append(')').toString();
    }

    private void hookChatActivityMenu(ClassLoader classLoader) {
        try {
            Class<?> chatActivityClass = classLoader.loadClass("org.telegram.ui.ChatActivity");
            Method createView = Reflect.method(chatActivityClass, "createView", Context.class);
            hook(createView, chain -> {
                Object result = chain.proceed();
                try {
                    injectChatMenu(chain.getThisObject());
                } catch (Throwable throwable) {
                    error("Chat menu injection failed", throwable);
                }
                return result;
            });
            info("Hooked ChatActivity menu");
        } catch (Throwable throwable) {
            error("Failed to hook ChatActivity menu", throwable);
        }
    }

    private void hookChatActivityAdapter(ClassLoader classLoader) {
        try {
            Class<?> adapterClass = classLoader.loadClass("org.telegram.ui.ChatActivity$ChatActivityAdapter");
            Class<?> viewHolderClass = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$ViewHolder");
            Method onBindViewHolder = Reflect.method(adapterClass, "onBindViewHolder", viewHolderClass, int.class);
            deoptimize(onBindViewHolder, "ChatActivityAdapter.onBindViewHolder(ViewHolder, int)");
            hook(onBindViewHolder, this::handleChatRowBinding);
            info("Hooked ChatActivityAdapter.onBindViewHolder(ViewHolder, int)");
        } catch (Throwable throwable) {
            error("Failed to hook ChatActivityAdapter", throwable);
        }
    }

    private void hookRecyclerViewBinding(ClassLoader classLoader) {
        try {
            Class<?> adapterClass = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$Adapter");
            Class<?> recyclerClass = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$Recycler");
            Class<?> viewHolderClass = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$ViewHolder");

            Method bindViewHolder = Reflect.method(adapterClass, "bindViewHolder", viewHolderClass, int.class);
            deoptimize(bindViewHolder, "RecyclerView.Adapter.bindViewHolder(ViewHolder, int)");
            hook(bindViewHolder, this::handleRecyclerViewBinding);
            info("Hooked RecyclerView.Adapter.bindViewHolder(ViewHolder, int)");

            Method onViewAttachedToWindow = Reflect.method(adapterClass, "onViewAttachedToWindow", viewHolderClass);
            deoptimize(onViewAttachedToWindow, "RecyclerView.Adapter.onViewAttachedToWindow(ViewHolder)");
            hook(onViewAttachedToWindow, this::handleRecyclerViewAttachment);
            info("Hooked RecyclerView.Adapter.onViewAttachedToWindow(ViewHolder)");

            Method tryBindViewHolderByDeadline = Reflect.method(
                    recyclerClass,
                    "tryBindViewHolderByDeadline",
                    viewHolderClass,
                    int.class,
                    int.class,
                    long.class
            );
            deoptimize(tryBindViewHolderByDeadline, "RecyclerView.Recycler.tryBindViewHolderByDeadline(ViewHolder, int, int, long)");
        } catch (Throwable throwable) {
            error("Failed to hook RecyclerView binding", throwable);
        }
    }

    private void hookProfileSettingsMenu(ClassLoader classLoader) {
        try {
            Class<?> profileActivityClass = classLoader.loadClass("org.telegram.ui.ProfileActivity");
            Method createActionBarMenu = Reflect.method(profileActivityClass, "createActionBarMenu", boolean.class);
            hook(createActionBarMenu, chain -> {
                Object result = chain.proceed();
                try {
                    injectGlobalSettingsMenu(chain.getThisObject(), true);
                } catch (Throwable throwable) {
                    error("ProfileActivity menu injection failed", throwable);
                }
                return result;
            });
            info("Hooked ProfileActivity settings menu");
        } catch (Throwable throwable) {
            error("Failed to hook ProfileActivity menu", throwable);
        }
    }

    private void hookSettingsActivityMenu(ClassLoader classLoader) {
        try {
            Class<?> settingsActivityClass = classLoader.loadClass("org.telegram.ui.SettingsActivity");
            Method createView = Reflect.method(settingsActivityClass, "createView", Context.class);
            hook(createView, chain -> {
                Object result = chain.proceed();
                try {
                    injectGlobalSettingsMenu(chain.getThisObject(), false);
                } catch (Throwable throwable) {
                    error("SettingsActivity menu injection failed", throwable);
                }
                return result;
            });
            info("Hooked SettingsActivity menu");
        } catch (Throwable throwable) {
            error("Failed to hook SettingsActivity menu", throwable);
        }
    }

    private Object handleMessageBinding(XposedInterface.Chain chain) throws Throwable {
        Object cell = chain.getThisObject();
        Object messageObject = chain.getArg(0);
        emitHookEntry("message", cell, messageObject);
        Object result = chain.proceed();
        try {
            applyDecision(cell, messageObject);
        } catch (Throwable throwable) {
            error("Message filtering failed", throwable);
        }
        return result;
    }

    private Object handleCellLifecycle(XposedInterface.Chain chain) throws Throwable {
        emitHookEntry("lifecycle", chain.getThisObject(), null);
        Object result = chain.proceed();
        try {
            applyDecisionToBoundViews(chain.getThisObject());
        } catch (Throwable throwable) {
            error("Cell lifecycle filtering failed", throwable);
        }
        return result;
    }

    private Object handleChatRowBinding(XposedInterface.Chain chain) throws Throwable {
        emitHookEntry("chatRow", chain.getThisObject(), chain.getArg(0));
        Object result = chain.proceed();
        try {
            Object holder = chain.getArg(0);
            Object itemView = Reflect.field(holder, "itemView");
            applyDecisionToBoundViews(itemView);
        } catch (Throwable throwable) {
            error("Chat row filtering failed", throwable);
        }
        return result;
    }

    private Object handleRecyclerViewBinding(XposedInterface.Chain chain) throws Throwable {
        emitHookEntry("recycler", chain.getThisObject(), chain.getArg(0));
        Object result = chain.proceed();
        try {
            Object holder = chain.getArg(0);
            Object itemView = Reflect.field(holder, "itemView");
            applyDecisionToBoundViews(itemView);
        } catch (Throwable throwable) {
            error("RecyclerView binding filter failed", throwable);
        }
        return result;
    }

    private Object handleRecyclerViewAttachment(XposedInterface.Chain chain) throws Throwable {
        emitHookEntry("attach", chain.getThisObject(), chain.getArg(0));
        Object result = chain.proceed();
        try {
            Object holder = chain.getArg(0);
            Object itemView = Reflect.field(holder, "itemView");
            applyDecisionToBoundViews(itemView);
        } catch (Throwable throwable) {
            error("RecyclerView attachment filter failed", throwable);
        }
        return result;
    }

    private void applyDecisionToBoundViews(Object rootCandidate) {
        BoundMessageViewWalker.visit(rootCandidate, this::applyDecision);
    }

    private void applyDecision(Object cell, Object messageObject) {
        if (!(cell instanceof View)) {
            return;
        }
        MessageSnapshot snapshot = TelegramMessageNormalizer.normalize(cell, messageObject);
        if (snapshot == null) {
            UiMutation.apply((View) cell, FilterDecision.allow(), "");
            return;
        }
        FilterConfig config = configProvider.getConfig(((View) cell).getContext().getApplicationContext());
        FilterDecision decision = decisionCache.get(config, snapshot, () -> filterEngine.evaluate(config, snapshot));
        UiMutation.apply((View) cell, decision, snapshot.stableKey());
        emitDecisionProbe(config, snapshot, decision);
        emitBindingProbe(config, cell, snapshot, decision);
        if (config.debugLogging && (decision.matched || decision.excluded)) {
            info("Decision=" + decision.reason + " dialog=" + snapshot.dialogId + " sender=" + snapshot.senderId + " msg=" + snapshot.messageId);
        }
    }

    private void emitHookEntry(String source, Object target, Object payload) {
        int remaining = hookEntryBudget.getAndDecrement();
        if (remaining <= 0) {
            return;
        }
        info(
                "HookEntry source=" + source
                        + " target=" + classNameOf(target)
                        + " payload=" + classNameOf(payload)
        );
    }

    private String classNameOf(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private void emitDecisionProbe(FilterConfig config, MessageSnapshot snapshot, FilterDecision decision) {
        int remaining = decisionProbeBudget.getAndDecrement();
        if (remaining <= 0) {
            return;
        }
        info(
                "DecisionProbe action=" + config.action
                        + " debug=" + config.debugLogging
                        + " globalRules=" + config.globalRules.size()
                        + " chatRules=" + config.chatRules.size()
                        + " updatedAt=" + config.updatedAtEpochMs
                        + " matched=" + decision.matched
                        + " excluded=" + decision.excluded
                        + " reason=" + preview(decision.reason)
                        + " chat=" + preview(snapshot.chatName)
                        + " sender=" + preview(snapshot.senderName)
                        + " dialog=" + snapshot.dialogId
                        + " text=" + preview(snapshot.text)
        );
    }

    private void emitBindingProbe(FilterConfig config, Object cell, MessageSnapshot snapshot, FilterDecision decision) {
        if (!config.debugLogging) {
            return;
        }
        int remaining = bindingProbeBudget.getAndDecrement();
        if (remaining <= 0) {
            return;
        }
        info(
                "BindProbe cell=" + cell.getClass().getSimpleName()
                        + " chat=" + preview(snapshot.chatName)
                        + " sender=" + preview(snapshot.senderName)
                        + " dialog=" + snapshot.dialogId
                        + " msg=" + snapshot.messageId
                        + " text=" + preview(snapshot.text)
                        + " caption=" + preview(snapshot.caption)
                        + " buttons=" + preview(snapshot.buttonText)
                        + " decision=" + decision.reason
        );
    }

    private String preview(String value) {
        if (value == null) {
            return "\"\"";
        }
        String normalized = value.replace('\n', ' ').trim();
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48) + "...";
        }
        return "\"" + normalized + "\"";
    }

    private void injectChatMenu(Object chatActivity) {
        Object headerItem = Reflect.field(chatActivity, "headerItem");
        if (headerItem == null || hasMenuItem(headerItem, MENU_ID_CHAT)) {
            return;
        }
        Context context = contextFromMenuItem(headerItem);
        int iconRes = resolveIcon(context);
        Object subItem = addMenuSubItem(headerItem, MENU_ID_CHAT, iconRes, moduleString(context, R.string.hook_menu_label_chat));
        if (!(subItem instanceof View)) {
            info("ChatActivity menu addSubItem unavailable on " + headerItem.getClass().getName());
            return;
        }
        View subItemView = (View) subItem;
        subItemView.setTag(R.id.gramsieve_menu_item_id, MENU_ID_CHAT);
        subItemView.setOnClickListener(v -> {
            try {
                long dialogId = Reflect.asLong(Reflect.invokeIfExists(chatActivity, "getDialogId", new Class<?>[0]), 0L);
                String title = resolveChatTitle(chatActivity);
                launchConfig(v.getContext(), ConfigDialogActivity.MODE_CHAT, dialogId, title);
            } finally {
                Reflect.invokeIfExists(headerItem, "toggleSubMenu", new Class<?>[0]);
            }
        });
    }

    private void injectGlobalSettingsMenu(Object host, boolean requireSettingsFlag) {
        if (requireSettingsFlag) {
            Object isSettings = Reflect.invokeIfExists(host, "isSettings", new Class<?>[0]);
            if (isSettings instanceof Boolean) {
                if (!((Boolean) isSettings)) {
                    return;
                }
            } else {
                info(host.getClass().getSimpleName() + ".isSettings unavailable; attempting fallback menu injection");
            }
        }
        Object otherItem = resolveOverflowMenuItem(host);
        if (otherItem == null) {
            info(host.getClass().getSimpleName() + " overflow menu item not found");
            return;
        }
        if (hasMenuItem(otherItem, MENU_ID_GLOBAL)) {
            return;
        }
        Context context = contextFromMenuItem(otherItem);
        int iconRes = resolveIcon(context);
        Object subItem = addMenuSubItem(otherItem, MENU_ID_GLOBAL, iconRes, moduleString(context, R.string.hook_menu_label_global));
        if (!(subItem instanceof View)) {
            info(host.getClass().getSimpleName() + " menu addSubItem unavailable on " + otherItem.getClass().getName());
            return;
        }
        View subItemView = (View) subItem;
        subItemView.setTag(R.id.gramsieve_menu_item_id, MENU_ID_GLOBAL);
        subItemView.setOnClickListener(v -> {
            try {
                launchConfig(v.getContext(), ConfigDialogActivity.MODE_GLOBAL, 0L, "");
            } finally {
                Reflect.invokeIfExists(otherItem, "toggleSubMenu", new Class<?>[0]);
            }
        });
    }

    private Object resolveOverflowMenuItem(Object host) {
        Object direct = Reflect.field(host, "otherItem");
        if (direct != null) {
            return direct;
        }
        Object actionBar = Reflect.field(host, "actionBar");
        if (actionBar == null) {
            return null;
        }
        Object menu = Reflect.field(actionBar, "menu");
        if (menu instanceof ViewGroup) {
            Object lastItem = lastActionBarMenuItem((ViewGroup) menu);
            if (lastItem != null) {
                return lastItem;
            }
        }
        if (actionBar instanceof ViewGroup) {
            return findMenuItemFromActionBar((ViewGroup) actionBar);
        }
        return null;
    }

    private Object findMenuItemFromActionBar(ViewGroup actionBar) {
        for (int i = actionBar.getChildCount() - 1; i >= 0; i--) {
            View child = actionBar.getChildAt(i);
            if (child instanceof ViewGroup) {
                Object lastItem = lastActionBarMenuItem((ViewGroup) child);
                if (lastItem != null) {
                    return lastItem;
                }
            }
        }
        return null;
    }

    private Object lastActionBarMenuItem(ViewGroup group) {
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child.getClass().getName().contains("ActionBarMenuItem")) {
                return child;
            }
        }
        return null;
    }

    private boolean hasMenuItem(Object menuItem, int targetId) {
        Object popupLayout = Reflect.invokeIfExists(menuItem, "getPopupLayout", new Class<?>[0]);
        if (!(popupLayout instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) popupLayout;
        for (int i = 0; i < group.getChildCount(); i++) {
            Object keyedTag = group.getChildAt(i).getTag(R.id.gramsieve_menu_item_id);
            if (keyedTag instanceof Integer && ((Integer) keyedTag) == targetId) {
                return true;
            }
            Object tag = group.getChildAt(i).getTag();
            if (tag instanceof Integer && ((Integer) tag) == targetId) {
                return true;
            }
        }
        return false;
    }

    private Object addMenuSubItem(Object menuItem, int menuId, int iconRes, CharSequence title) {
        Object subItem = Reflect.invokeIfExists(
                menuItem,
                "addSubItem",
                new Class<?>[]{int.class, int.class, CharSequence.class},
                menuId,
                iconRes,
                title
        );
        if (subItem != null) {
            return subItem;
        }
        return Reflect.invokeIfExists(
                menuItem,
                "addSubItem",
                new Class<?>[]{int.class, CharSequence.class},
                menuId,
                title
        );
    }

    private Context contextFromMenuItem(Object menuItem) {
        if (menuItem instanceof View) {
            return ((View) menuItem).getContext();
        }
        Object context = Reflect.invokeIfExists(menuItem, "getContext", new Class<?>[0]);
        if (context instanceof Context) {
            return (Context) context;
        }
        throw new IllegalStateException("Menu item is not a view: " + menuItem);
    }

    private int resolveIcon(Context context) {
        int telegramIcon = context.getResources().getIdentifier("msg_settings", "drawable", "org.telegram.messenger");
        return telegramIcon != 0 ? telegramIcon : android.R.drawable.ic_menu_manage;
    }

    private CharSequence moduleString(Context hostContext, int resId) {
        Resources resources = getModuleResources(hostContext);
        return resources != null ? resources.getString(resId) : MODULE_PACKAGE;
    }

    private Resources getModuleResources(Context hostContext) {
        if (moduleResources != null) {
            return moduleResources;
        }
        try {
            Context moduleContext = hostContext.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            moduleResources = moduleContext.getResources();
            return moduleResources;
        } catch (PackageManager.NameNotFoundException exception) {
            error("Failed to create module package context", exception);
            return null;
        } catch (Throwable throwable) {
            error("Failed to create module resources", throwable);
            return null;
        }
    }

    private String resolveChatTitle(Object chatActivity) {
        Object currentChat = Reflect.field(chatActivity, "currentChat");
        String chatTitle = Reflect.asString(Reflect.field(currentChat, "title")).trim();
        if (!chatTitle.isBlank()) {
            return chatTitle;
        }
        Object currentUser = Reflect.field(chatActivity, "currentUser");
        String first = Reflect.asString(Reflect.field(currentUser, "first_name")).trim();
        String last = Reflect.asString(Reflect.field(currentUser, "last_name")).trim();
        String full = (first + " " + last).trim();
        if (!full.isBlank()) {
            return full;
        }
        return Reflect.asString(Reflect.field(currentUser, "username")).trim();
    }

    private void launchConfig(Context context, String mode, long dialogId, String title) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(MODULE_PACKAGE, ConfigDialogActivity.class.getName()));
        intent.putExtra(ConfigDialogActivity.EXTRA_MODE, mode);
        if (ConfigDialogActivity.MODE_CHAT.equals(mode)) {
            intent.putExtra(ConfigDialogActivity.EXTRA_DIALOG_ID, dialogId);
            intent.putExtra(ConfigDialogActivity.EXTRA_DIALOG_TITLE, title);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void logTelegramVersion(ClassLoader classLoader, ApplicationInfo applicationInfo) {
        String buildVersion = "";
        try {
            Class<?> buildVarsClass = classLoader.loadClass("org.telegram.messenger.BuildVars");
            Object raw = Reflect.staticField(buildVarsClass, "BUILD_VERSION_STRING");
            buildVersion = Reflect.asString(raw).trim();
        } catch (Throwable ignored) {
            buildVersion = "";
        }
        String suffix = buildVersion.isBlank() ? "" : " build=" + buildVersion;
        info("Target Telegram package=" + applicationInfo.packageName + suffix + " source=" + applicationInfo.sourceDir);
    }

    private void hook(Method method, XposedInterface.Hooker hooker) {
        module.hook(method)
                .setPriority(XposedInterface.PRIORITY_LOWEST)
                .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                .intercept(hooker);
    }

    private void deoptimize(Method method, String label) {
        try {
            boolean changed = module.deoptimize(method);
            info((changed ? "Deoptimized " : "Deopt not needed for ") + label);
        } catch (Throwable throwable) {
            error("Failed to deoptimize " + label, throwable);
        }
    }

    private void info(String message) {
        Log.i(TAG, message);
        module.log(Log.INFO, TAG, message);
    }

    private void error(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
        module.log(Log.ERROR, TAG, message, throwable);
    }
}
