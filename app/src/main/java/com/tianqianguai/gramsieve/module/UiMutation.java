package com.tianqianguai.gramsieve.module;

import android.view.View;
import android.view.ViewGroup;

import com.tianqianguai.gramsieve.R;
import com.tianqianguai.gramsieve.core.FilterConfig;
import com.tianqianguai.gramsieve.core.FilterDecision;

final class UiMutation {
    private UiMutation() {
    }

    static void apply(View view, FilterDecision decision, String messageKey) {
        if (view == null) {
            return;
        }
        ViewState state = ensureState(view);
        view.setTag(R.id.gramsieve_last_message_key, messageKey);
        if (decision.matched) {
            if (decision.action == FilterConfig.Action.DEBUG_MARK) {
                restore(view, state);
                view.setAlpha(0.35f);
                disableInteractions(view);
            } else if (decision.action == FilterConfig.Action.COLLAPSE) {
                collapse(view, state);
            } else {
                hide(view, state);
            }
            return;
        }
        restore(view, state);
    }

    private static ViewState ensureState(View view) {
        Object tag = view.getTag(R.id.gramsieve_view_state);
        if (tag instanceof ViewState) {
            return (ViewState) tag;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        ViewState state = new ViewState(
                view.getVisibility(),
                view.getAlpha(),
                layoutParams == null ? ViewGroup.LayoutParams.WRAP_CONTENT : layoutParams.height,
                view.isClickable(),
                view.isLongClickable(),
                view.isEnabled()
        );
        view.setTag(R.id.gramsieve_view_state, state);
        return state;
    }

    private static void hide(View view, ViewState state) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.height = 0;
            view.setLayoutParams(layoutParams);
        }
        view.setAlpha(0f);
        view.setVisibility(View.GONE);
        disableInteractions(view);
    }

    private static void collapse(View view, ViewState state) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null) {
            int collapsedHeight = Math.round(24f * view.getResources().getDisplayMetrics().density);
            int targetHeight = state.originalHeight > 0 ? Math.min(state.originalHeight, collapsedHeight) : collapsedHeight;
            layoutParams.height = targetHeight;
            view.setLayoutParams(layoutParams);
        }
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0.18f);
        disableInteractions(view);
    }

    private static void restore(View view, ViewState state) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.height = state.originalHeight == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : state.originalHeight;
            view.setLayoutParams(layoutParams);
        }
        view.setAlpha(state.originalAlpha);
        view.setVisibility(state.originalVisibility);
        view.setClickable(state.originalClickable);
        view.setLongClickable(state.originalLongClickable);
        view.setEnabled(state.originalEnabled);
    }

    private static void disableInteractions(View view) {
        view.setClickable(false);
        view.setLongClickable(false);
        view.setEnabled(false);
    }

    private static final class ViewState {
        final int originalVisibility;
        final float originalAlpha;
        final int originalHeight;
        final boolean originalClickable;
        final boolean originalLongClickable;
        final boolean originalEnabled;

        ViewState(int originalVisibility, float originalAlpha, int originalHeight, boolean originalClickable, boolean originalLongClickable, boolean originalEnabled) {
            this.originalVisibility = originalVisibility;
            this.originalAlpha = originalAlpha;
            this.originalHeight = originalHeight;
            this.originalClickable = originalClickable;
            this.originalLongClickable = originalLongClickable;
            this.originalEnabled = originalEnabled;
        }
    }
}
