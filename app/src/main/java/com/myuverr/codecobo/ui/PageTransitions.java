// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui;

import android.animation.*;
import android.os.Looper;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import java.util.EnumMap;

/** Animates transitions between existing panel views. */
final class PageTransitions implements AutoCloseable {
    private final FrameLayout content;
    private final EnumMap<MainNavigation.Page, PageLayer> layers = new EnumMap<>(MainNavigation.Page.class);
    private final ValueAnimator.DurationScaleChangeListener scaleChanges;
    private final View.OnLayoutChangeListener layoutChanges = (v,l,t,r,b,ol,ot,or,ob) -> {
        if (r-l != or-ol || b-t != ob-ot) finish();
    };
    private MainNavigation.Page selected;
    private PageLayer current;
    private AnimatorSet motion;
    private long generation;
    private boolean closed;

    PageTransitions(FrameLayout content) {
        this.content = content;
        scaleChanges = scale -> {
            if (scale == 0f && !closed) {
                if (Looper.myLooper() == Looper.getMainLooper()) finish();
                else content.post(() -> { if (!closed) finish(); });
            }
        };
        content.setClipChildren(true);
        content.addOnLayoutChangeListener(layoutChanges);
        ValueAnimator.registerDurationScaleChangeListener(scaleChanges);
    }

    void show(MainNavigation.Page page, View panel) {
        if (closed || page == selected) return;
        var outgoing = current;
        var previous = selected;
        stopMotion();
        var incoming = layers.computeIfAbsent(page, key -> {
            var layer = new PageLayer(content);
            layer.addView(panel, new FrameLayout.LayoutParams(-1, -1));
            return layer;
        });
        for (var layer : layers.values()) {
            if (layer != incoming && layer != outgoing) hide(layer);
        }
        boolean retained = incoming.getParent() == content && incoming.getVisibility() == View.VISIBLE;
        float direction = previous == null || page.ordinal() > previous.ordinal() ? 1f : -1f;
        if (content.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) direction = -direction;
        float distance = 36f * content.getResources().getDisplayMetrics().density;
        if (!retained) {
            incoming.setAlpha(0f);
            incoming.setTranslationX(direction * distance);
        }
        if (incoming.getParent() != content)
            content.addView(incoming, new FrameLayout.LayoutParams(-1, -1));
        incoming.setVisibility(View.VISIBLE);
        selected = page;
        current = incoming;
        incoming.active(true);
        incoming.bringToFront();
        if (outgoing != null) outgoing.active(false);

        if (outgoing == null || !content.isLaidOut() || !ValueAnimator.areAnimatorsEnabled()) {
            finish();
            return;
        }
        long token = generation;
        var spatial = new PathInterpolator(.4f, 0f, .2f, 1f);
        var enterX = ObjectAnimator.ofFloat(incoming, View.TRANSLATION_X, incoming.getTranslationX(), 0f);
        enterX.setDuration(280); enterX.setInterpolator(spatial);
        var enterAlpha = ObjectAnimator.ofFloat(incoming, View.ALPHA, incoming.getAlpha(), 1f);
        enterAlpha.setStartDelay(40); enterAlpha.setDuration(220); enterAlpha.setInterpolator(spatial);
        var exitX = ObjectAnimator.ofFloat(outgoing, View.TRANSLATION_X, outgoing.getTranslationX(), -direction * distance);
        exitX.setDuration(240); exitX.setInterpolator(spatial);
        var exitAlpha = ObjectAnimator.ofFloat(outgoing, View.ALPHA, outgoing.getAlpha(), 0f);
        exitAlpha.setDuration(110); exitAlpha.setInterpolator(spatial);
        motion = new AnimatorSet();
        motion.playTogether(enterX, enterAlpha, exitX, exitAlpha);
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!closed && generation == token) finish();
            }
        });
        motion.start();
    }

    private void stopMotion() {
        generation++;
        if (motion != null) {
            motion.removeAllListeners();
            motion.cancel();
            motion = null;
        }
    }

    void finish() {
        stopMotion();
        for (var layer : layers.values()) {
            if (layer == current) {
                layer.setAlpha(1f);
                layer.setTranslationX(0f);
                layer.active(true);
            } else hide(layer);
        }
    }

    private void hide(PageLayer layer) {
        layer.active(false);
        // Retained Compose layers must stay attached to preserve dynamic outline observation.
        layer.setVisibility(View.GONE);
        layer.setAlpha(1f);
        layer.setTranslationX(0f);
    }

    @Override public void close() {
        if (closed) return;
        finish();
        closed = true;
        content.removeOnLayoutChangeListener(layoutChanges);
        ValueAnimator.unregisterDurationScaleChangeListener(scaleChanges);
        for (var layer : layers.values()) {
            layer.active(false);
            if (layer.getParent() == content) content.removeView(layer);
        }
        layers.clear();
        current = null;
    }

    /** Hides outgoing views from input, focus, and accessibility. */
    private static final class PageLayer extends FrameLayout {
        private boolean active;
        PageLayer(FrameLayout parent) { super(parent.getContext()); }
        void active(boolean value) {
            active = value;
            setDescendantFocusability(value ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
            if (!value) clearFocus();
            setImportantForAccessibility(value ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            return active && super.dispatchTouchEvent(event);
        }
    }
}
