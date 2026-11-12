package com.ahmed.hzunlock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * An almost-invisible surface that redraws every frame and explicitly asks
 * SurfaceFlinger for the target rate. SurfaceFlinger picks the highest rate
 * any visible, updating layer asks for, so this pulls the whole screen up —
 * the same way games that are allowed 120Hz do it.
 */
final class RateKeeper implements SurfaceHolder.Callback, Choreographer.FrameCallback {
    private final SurfaceView view;
    private final float hz;
    private Surface surface;
    private boolean alive;

    RateKeeper(Context c, float hz) {
        this.hz = hz;
        view = new SurfaceView(c);
        view.setZOrderOnTop(true);
        view.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        view.getHolder().addCallback(this);
    }

    SurfaceView view() { return view; }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        surface = h.getSurface();
        try {
            surface.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS);
        } catch (Throwable ignored) {}
        alive = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder h) { stop(); }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!alive || surface == null || !surface.isValid()) return;
        try {
            Canvas cv = surface.lockHardwareCanvas();
            // A practically invisible pixel value: the layer still counts as updating.
            cv.drawColor(0x01000000, PorterDuff.Mode.SRC);
            surface.unlockCanvasAndPost(cv);
        } catch (Throwable ignored) {}
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        alive = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }
}
