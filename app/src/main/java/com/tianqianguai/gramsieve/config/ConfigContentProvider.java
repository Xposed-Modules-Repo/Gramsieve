package com.tianqianguai.gramsieve.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tianqianguai.gramsieve.core.FilterConfig;

public final class ConfigContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.tianqianguai.gramsieve.config";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET_CONFIG = "getConfig";
    public static final String KEY_CONFIG_JSON = "config_json";
    public static final String KEY_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (!METHOD_GET_CONFIG.equals(method)) {
            return super.call(method, arg, extras);
        }
        Bundle bundle = new Bundle();
        if (getContext() == null) {
            bundle.putString(KEY_CONFIG_JSON, ModuleConfigStore.toJson(FilterConfig.createDefault()));
            bundle.putLong(KEY_UPDATED_AT_EPOCH_MS, 0L);
            return bundle;
        }
        FilterConfig config = ModuleConfigStore.load(getContext());
        bundle.putString(KEY_CONFIG_JSON, ModuleConfigStore.toJson(config));
        bundle.putLong(KEY_UPDATED_AT_EPOCH_MS, config.updatedAtEpochMs);
        return bundle;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
