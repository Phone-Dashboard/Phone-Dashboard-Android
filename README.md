# Phone Dashboard - Android Mobile App

This document describes how to build and deploy Phone Dashboard, the mobile app component of the larger [Phone Dashboard](https://github.com/Phone-Dashboard) platform, which consists of this app and one or more data collection servers (which are not covered here - see [the server software repository](https://github.com/lenasong/Phone-Dashboard-Django)).

To build Phone Dashboard, a local installation of the Android software development kit is required. On desktop platforms, the quickest way to get started is to [download and install Android Studio](https://developer.android.com/studio).

Once Android Studio is installed, clone this repository to a local folder and be sure that the Passive Data Kit Git submodule is also checked out and populated:

    git submodule init; git submodule update

In the project home directory, copy the `empty.properties` file to `gradle.properties`. Update the `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` variables to contain your local Android signing key information

Once that is finished, open the cloned folder in Android Studio.

Within the `app/build.gradle` file, update the `android.defaultConfig.applicationId` field to [a new valid Android application ID](https://developer.android.com/build/configure-app-module) that you will be using to distribute the app. Leave the `android.namespace` parameter as is.

In the `app/src/main/res/values/` folder, rename the `keys.xml-template` file to `keys.xml` and populate it with the appropriate [App Center](https://appcenter.ms/) key if you wish to enable remote crash reporting.

In the `app/src/main/res/values/strings.xml` file in that same folder, configure the `url_phone_dashboard_configuration`, `url_phone_study_configuration`, and `url_phone_enroll` parameters to point to analagous locations on [your local Phone Dashboard data collection server](https://github.com/Phone-Dashboard/Phone-Dashboard-Django). If you'd like, you may also rename the app itself by changing the `app_name` parameter.

Once these steps are complete, you should be able to build a debug version for testing on a physical device. If you seek to distribute the application on the Google Play Store, you will need [an Android keystore](https://developer.android.com/studio/publish/app-signing) set up for signing. If you do this, update the `gradle.properties` file accordingly.
