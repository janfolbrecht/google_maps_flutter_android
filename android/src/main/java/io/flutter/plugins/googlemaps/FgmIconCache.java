// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.googlemaps;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.maps.model.BitmapDescriptor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fork addition: one {@link BitmapDescriptor} per distinct marker icon.
 *
 * <p>Upstream makes the icon anew for every marker: an asset with a size is opened, decoded and
 * scaled, bytes are decoded and scaled, and the resulting bitmap is handed to the Maps SDK, which
 * keeps a copy of its own. A map of a thousand equal pins therefore decodes a thousand bitmaps on
 * the platform thread and holds a thousand textures. The Maps SDK shares what it holds between
 * markers that are given the same descriptor, so the descriptor is kept by what it was made from.
 *
 * <p>Bounded, least recently used first out: an app that draws a different bitmap for every marker
 * must not grow it without limit. Used from the platform thread only.
 */
final class FgmIconCache {
  static final int MAX_ENTRIES = 256;

  private final boolean enabled;
  private final LinkedHashMap<Object, BitmapDescriptor> descriptors =
      new LinkedHashMap<Object, BitmapDescriptor>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Object, BitmapDescriptor> eldest) {
          return size() > MAX_ENTRIES;
        }
      };

  FgmIconCache() {
    this(FgmPerf.ICON_CACHE);
  }

  FgmIconCache(boolean enabled) {
    this.enabled = enabled;
  }

  /** The descriptor of an asset icon, made by {@code make} only the first time. */
  static BitmapDescriptor forAsset(
      @Nullable FgmIconCache cache,
      @NonNull PlatformBitmapAssetMap asset,
      float density,
      @NonNull Supplier<BitmapDescriptor> make) {
    if (cache == null || !cache.enabled) {
      return make.get();
    }
    return cache.get(
        new Key(
            asset.getAssetName(),
            null,
            asset.getBitmapScaling(),
            asset.getImagePixelRatio(),
            asset.getWidth(),
            asset.getHeight(),
            density),
        make);
  }

  /** The descriptor of an icon given as bytes, made by {@code make} only the first time. */
  static BitmapDescriptor forBytes(
      @Nullable FgmIconCache cache,
      @NonNull PlatformBitmapBytesMap bytes,
      float density,
      @NonNull Supplier<BitmapDescriptor> make) {
    if (cache == null || !cache.enabled) {
      return make.get();
    }
    return cache.get(
        new Key(
            null,
            bytes.getByteData(),
            bytes.getBitmapScaling(),
            bytes.getImagePixelRatio(),
            bytes.getWidth(),
            bytes.getHeight(),
            density),
        make);
  }

  int size() {
    return descriptors.size();
  }

  private BitmapDescriptor get(Key key, Supplier<BitmapDescriptor> make) {
    BitmapDescriptor descriptor = descriptors.get(key);
    if (descriptor == null) {
      descriptor = make.get();
      if (descriptor != null) {
        descriptors.put(key, descriptor);
      }
    }
    return descriptor;
  }

  /** Everything the icon is made from; two keys are equal when the icons would be. */
  private static final class Key {
    private final @Nullable String assetName;
    private final @Nullable byte[] bytes;
    private final PlatformMapBitmapScaling scaling;
    private final double imagePixelRatio;
    private final @Nullable Double width;
    private final @Nullable Double height;
    private final float density;
    private final int hash;

    Key(
        @Nullable String assetName,
        @Nullable byte[] bytes,
        PlatformMapBitmapScaling scaling,
        double imagePixelRatio,
        @Nullable Double width,
        @Nullable Double height,
        float density) {
      this.assetName = assetName;
      this.bytes = bytes;
      this.scaling = scaling;
      this.imagePixelRatio = imagePixelRatio;
      this.width = width;
      this.height = height;
      this.density = density;
      this.hash =
          31 * Objects.hash(assetName, scaling, imagePixelRatio, width, height, density)
              + Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Key)) {
        return false;
      }
      Key that = (Key) other;
      return hash == that.hash
          && Objects.equals(assetName, that.assetName)
          && scaling == that.scaling
          && imagePixelRatio == that.imagePixelRatio
          && Objects.equals(width, that.width)
          && Objects.equals(height, that.height)
          && density == that.density
          && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }
}
