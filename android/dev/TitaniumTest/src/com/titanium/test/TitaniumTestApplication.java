/* AUTO-GENERATED FILE.  DO NOT MODIFY.
 *
 * This class was automatically generated by
 * Appcelerator. It should not be modified by hand.
 */

package com.titanium.test;

import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.runtime.v8.V8Runtime;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiRootActivity;

public final class TitaniumTestApplication extends TiApplication {
    private static final String TAG = "TitaniumTestApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        appInfo = new TitaniumTestAppInfo(this);
        postAppInfo();

        // KrollAssetHelper.setAssetCrypt(new AssetCryptImpl());
        V8Runtime runtime = new V8Runtime();
        KrollRuntime.init(this, runtime);
        postOnCreate();
    }

    @Override
    public void verifyCustomModules(TiRootActivity rootActivity) {
    }
}