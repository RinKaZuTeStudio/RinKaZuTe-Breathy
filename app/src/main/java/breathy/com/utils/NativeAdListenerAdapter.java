package breathy.com.utils;

import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAd;
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAdListener;
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo;
import com.ironsource.mediationsdk.logger.IronSourceError;

/**
 * Java-side adapter for {@link LevelPlayNativeAdListener}.
 *
 * The LevelPlay native listener is compiled from Kotlin and enforces exact
 * nullability on its parameters, which makes overriding it directly from
 * Kotlin brittle across SDK versions. Implementing the overrides in Java
 * (no nullability enforcement) keeps the integration stable.
 *
 * Kotlin call sites subclass this and override {@link #onLoaded(LevelPlayNativeAd)}
 * / {@link #onLoadFailed(LevelPlayNativeAd)} only.
 */
public abstract class NativeAdListenerAdapter implements LevelPlayNativeAdListener {

    @Override
    public void onAdLoaded(LevelPlayNativeAd nativeAd, AdInfo adInfo) {
        onLoaded(nativeAd);
    }

    @Override
    public void onAdLoadFailed(LevelPlayNativeAd nativeAd, IronSourceError error) {
        onLoadFailed(nativeAd);
    }

    @Override
    public void onAdClicked(LevelPlayNativeAd nativeAd, AdInfo adInfo) {
        // No-op — the SDK tracks clicks on registered native views.
    }

    @Override
    public void onAdImpression(LevelPlayNativeAd nativeAd, AdInfo adInfo) {
        // No-op — the SDK tracks impressions on registered native views.
    }

    /** Called when the native ad is loaded and ready to render. */
    protected abstract void onLoaded(LevelPlayNativeAd nativeAd);

    /** Called when the native ad failed to load. */
    protected abstract void onLoadFailed(LevelPlayNativeAd nativeAd);
}
