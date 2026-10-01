// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.googlemaps;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Fork addition: switches and measurement of the marker path.
 *
 * <p>Everything is read once per process from Android system properties, which `adb shell setprop`
 * sets without a rebuild:
 *
 * <ul>
 *   <li>{@code debug.fgm.perf=1} logs one {@code [FGMPerf] batch} line per marker batch with its
 *       phases, and every gap of {@link #HITCH_THRESHOLD_MS} or more between two frames of the
 *       main thread as {@code [FGMPerf] hitch}. Off by default.
 *   <li>{@code debug.fgm.opt.<name>=0} switches one optimisation of the fork off. Every
 *       optimisation is on unless its property says {@code 0}.
 * </ul>
 */
final class FgmPerf {
  private static final String TAG = "FGMPerf";
  private static final long HITCH_THRESHOLD_MS = 34;

  static final boolean ENABLED = "1".equals(systemProperty("debug.fgm.perf"));

  /** One descriptor per distinct marker icon, see {@link FgmIconCache}. */
  static final boolean ICON_CACHE = optimisation("icon_cache");

  /** The switches of this process, for the first words of a measurement. */
  static final String SWITCHES = "icon_cache=" + (ICON_CACHE ? 1 : 0);

  // Accumulated inside one batch; the marker path runs on the platform thread only.
  private static long iconNanos;
  private static int iconCalls;
  private static long sdkAddNanos;
  private static long sdkRemoveNanos;
  private static boolean hitchMonitorStarted;

  private FgmPerf() {}

  /** Whether the optimisation {@code name} is on: it is unless {@code debug.fgm.opt.name} is 0. */
  private static boolean optimisation(String name) {
    return !"0".equals(systemProperty("debug.fgm.opt." + name));
  }

  private static String systemProperty(String key) {
    try {
      // android.os.SystemProperties is not in the SDK; there is no public way to read a
      // property that adb can set on a release build.
      Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
      Object value = get.invoke(null, key);
      return value == null ? "" : value.toString();
    } catch (Exception | LinkageError e) {
      return "";
    }
  }

  static long now() {
    return ENABLED ? System.nanoTime() : 0;
  }

  static void addIconTime(long startNanos) {
    if (!ENABLED) {
      return;
    }
    iconNanos += System.nanoTime() - startNanos;
    iconCalls++;
  }

  static void addSdkAddTime(long startNanos) {
    if (ENABLED) {
      sdkAddNanos += System.nanoTime() - startNanos;
    }
  }

  static void addSdkRemoveTime(long startNanos) {
    if (ENABLED) {
      sdkRemoveNanos += System.nanoTime() - startNanos;
    }
  }

  static void beginBatch() {
    if (!ENABLED) {
      return;
    }
    startHitchMonitor();
    iconNanos = 0;
    iconCalls = 0;
    sdkAddNanos = 0;
    sdkRemoveNanos = 0;
    Trace.beginSection("FGM updateMarkers");
  }

  static void endBatch(
      int adds,
      int changes,
      int removes,
      long beginNanos,
      long addedNanos,
      long changedNanos,
      long endNanos,
      long beginUptimeMs,
      int markersAfter) {
    if (!ENABLED) {
      return;
    }
    Trace.endSection();
    Log.i(
        TAG,
        String.format(
            Locale.US,
            "[FGMPerf] batch adds=%d changes=%d removes=%d add_ms=%.2f change_ms=%.2f"
                + " remove_ms=%.2f total_ms=%.2f icon_ms=%.2f icon_calls=%d sdk_add_ms=%.2f"
                + " sdk_remove_ms=%.2f markers=%d t_begin=%d t_end=%d %s",
            adds,
            changes,
            removes,
            ms(addedNanos - beginNanos),
            ms(changedNanos - addedNanos),
            ms(endNanos - changedNanos),
            ms(endNanos - beginNanos),
            ms(iconNanos),
            iconCalls,
            ms(sdkAddNanos),
            ms(sdkRemoveNanos),
            markersAfter,
            beginUptimeMs,
            SystemClock.uptimeMillis(),
            SWITCHES));
  }

  private static double ms(long nanos) {
    return nanos / 1e6;
  }

  /**
   * Logs every stretch the main thread went without a frame callback. It holds whatever kept the
   * thread: decoding the batch, applying it, a touch handler.
   */
  private static void startHitchMonitor() {
    if (hitchMonitorStarted) {
      return;
    }
    hitchMonitorStarted = true;
    new Handler(Looper.getMainLooper())
        .post(
            () ->
                Choreographer.getInstance()
                    .postFrameCallback(
                        new Choreographer.FrameCallback() {
                          private long lastMs = SystemClock.uptimeMillis();

                          @Override
                          public void doFrame(long frameTimeNanos) {
                            long nowMs = SystemClock.uptimeMillis();
                            if (nowMs - lastMs >= HITCH_THRESHOLD_MS) {
                              Log.i(
                                  TAG,
                                  "[FGMPerf] hitch gap_ms=" + (nowMs - lastMs) + " t_end=" + nowMs);
                            }
                            lastMs = nowMs;
                            Choreographer.getInstance().postFrameCallback(this);
                          }
                        }));
  }
}
