// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.googlemaps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import com.google.android.gms.maps.model.BitmapDescriptor;
import java.util.function.Supplier;
import org.junit.Test;

public class FgmIconCacheTest {
  private int made;

  private final Supplier<BitmapDescriptor> make =
      () -> {
        made++;
        return mock(BitmapDescriptor.class);
      };

  private static PlatformBitmapAssetMap asset(String name, Double width, Double height) {
    return new PlatformBitmapAssetMap(name, PlatformMapBitmapScaling.AUTO, 3.0, width, height);
  }

  private static PlatformBitmapBytesMap bytes(byte[] data, Double width) {
    return new PlatformBitmapBytesMap(data, PlatformMapBitmapScaling.AUTO, 3.0, width, width);
  }

  @Test
  public void equalAssetIconsShareOneDescriptor() {
    FgmIconCache cache = new FgmIconCache(true);

    BitmapDescriptor first = FgmIconCache.forAsset(cache, asset("pin.png", 44.0, 44.0), 2.5f, make);
    BitmapDescriptor second =
        FgmIconCache.forAsset(cache, asset("pin.png", 44.0, 44.0), 2.5f, make);

    assertSame(first, second);
    assertEquals(1, made);
  }

  @Test
  public void whateverChangesTheBitmapMakesAnotherDescriptor() {
    FgmIconCache cache = new FgmIconCache(true);
    BitmapDescriptor base = FgmIconCache.forAsset(cache, asset("pin.png", 44.0, 44.0), 2.5f, make);

    assertNotSame(base, FgmIconCache.forAsset(cache, asset("red.png", 44.0, 44.0), 2.5f, make));
    assertNotSame(base, FgmIconCache.forAsset(cache, asset("pin.png", 30.0, 44.0), 2.5f, make));
    assertNotSame(base, FgmIconCache.forAsset(cache, asset("pin.png", 44.0, null), 2.5f, make));
    assertNotSame(base, FgmIconCache.forAsset(cache, asset("pin.png", 44.0, 44.0), 3.0f, make));
    assertNotSame(
        base,
        FgmIconCache.forAsset(
            cache,
            new PlatformBitmapAssetMap("pin.png", PlatformMapBitmapScaling.NONE, 3.0, 44.0, 44.0),
            2.5f,
            make));
    assertNotSame(
        base,
        FgmIconCache.forAsset(
            cache,
            new PlatformBitmapAssetMap("pin.png", PlatformMapBitmapScaling.AUTO, 2.0, 44.0, 44.0),
            2.5f,
            make));
    assertEquals(7, made);
  }

  @Test
  public void bytesAreComparedByContent() {
    FgmIconCache cache = new FgmIconCache(true);

    BitmapDescriptor first =
        FgmIconCache.forBytes(cache, bytes(new byte[] {1, 2, 3}, 44.0), 2.5f, make);
    BitmapDescriptor same =
        FgmIconCache.forBytes(cache, bytes(new byte[] {1, 2, 3}, 44.0), 2.5f, make);
    BitmapDescriptor other =
        FgmIconCache.forBytes(cache, bytes(new byte[] {1, 2, 4}, 44.0), 2.5f, make);

    assertSame(first, same);
    assertNotSame(first, other);
    assertEquals(2, made);
  }

  @Test
  public void anAssetAndBytesNeverShareADescriptor() {
    FgmIconCache cache = new FgmIconCache(true);

    BitmapDescriptor fromAsset =
        FgmIconCache.forAsset(cache, asset("pin.png", 44.0, 44.0), 2.5f, make);
    BitmapDescriptor fromBytes = FgmIconCache.forBytes(cache, bytes(new byte[0], 44.0), 2.5f, make);

    assertNotSame(fromAsset, fromBytes);
  }

  @Test
  public void switchedOffMakesTheIconEveryTime() {
    FgmIconCache cache = new FgmIconCache(false);

    FgmIconCache.forAsset(cache, asset("pin.png", 44.0, 44.0), 2.5f, make);
    FgmIconCache.forAsset(cache, asset("pin.png", 44.0, 44.0), 2.5f, make);

    assertEquals(2, made);
    assertEquals(0, cache.size());
  }

  @Test
  public void aWrapperWithoutACacheMakesTheIconEveryTime() {
    // A mocked BitmapDescriptorFactoryWrapper has no cache.
    FgmIconCache.forAsset(null, asset("pin.png", 44.0, 44.0), 2.5f, make);
    FgmIconCache.forAsset(null, asset("pin.png", 44.0, 44.0), 2.5f, make);

    assertEquals(2, made);
  }

  @Test
  public void theLeastRecentlyUsedIconLeavesWhenTheCacheIsFull() {
    FgmIconCache cache = new FgmIconCache(true);
    BitmapDescriptor first = FgmIconCache.forAsset(cache, asset("0.png", 44.0, 44.0), 2.5f, make);
    for (int i = 1; i < FgmIconCache.MAX_ENTRIES; i++) {
      FgmIconCache.forAsset(cache, asset(i + ".png", 44.0, 44.0), 2.5f, make);
    }
    // Using the first again makes the second the eldest.
    assertSame(first, FgmIconCache.forAsset(cache, asset("0.png", 44.0, 44.0), 2.5f, make));

    FgmIconCache.forAsset(cache, asset("one-more.png", 44.0, 44.0), 2.5f, make);

    assertEquals(FgmIconCache.MAX_ENTRIES, cache.size());
    assertSame(first, FgmIconCache.forAsset(cache, asset("0.png", 44.0, 44.0), 2.5f, make));
    int before = made;
    FgmIconCache.forAsset(cache, asset("1.png", 44.0, 44.0), 2.5f, make);
    assertEquals(before + 1, made);
  }
}
