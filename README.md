# Phone Dashboard - Android Mobile App

This document describes how to build and deploy Phone Dashboard, the mobile app component of the larger [Phone Dashboard](https://github.com/Phone-Dashboard) platform, which consists of this app and one or more data collection servers (which are not covered here - see [the server software repository](https://github.com/lenasong/Phone-Dashboard-Django)).

To build Phone Dashboard, a local installation of the Android software development kit is required. On desktop platforms, the quickest way to get started is to [download and install Android Studio](https://developer.android.com/studio).

Once Android Studio is installed, clone this repository to a local folder and be sure that the Passive Data Kit Git submodule is also checked out and populated. Once that is finished, open the cloned folder in Android Studio.

At this point, Android Studio will likely complain about an unknown property called `mapbox_key`. Copy the `empty.properties` file to `gradle.properties` and retry the Gradle sync.
