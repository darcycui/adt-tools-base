/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.sdklib.repositoryv2.targets;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.google.common.base.Objects;

import java.io.File;

/**
 * {@link ISystemImage} based on a {@link RepoPackage} (either system image, platform, or addon).
 */
public class SystemImage implements ISystemImage {
    /**
     * Tag to apply to system images if none other is specified.
     */
    public static final IdDisplay DEFAULT_TAG = IdDisplay.create("default", "Default");

    /**
     * Tag to apply to system images for wearables.
     */
    public static final IdDisplay WEAR_TAG = IdDisplay.create("android-wear", "Android Wear");

    /**
     * Tag to apply to system images for Android TV.
     */
    public static final IdDisplay TV_TAG = IdDisplay.create("android-tv", "Android TV");

    /**
     * Tag to apply to system images for Glass.
     */
    public static final IdDisplay GLASS_TAG = IdDisplay.create("google_gdk", "Android Glass");

    /**
     * Tag to apply to system images that include Google APIs.
     * Note that {@link #WEAR_TAG} and {@link #TV_TAG} implies the presence of Google APIs in addition
     * there is one system image that uses {@link #GOOGLE_APIS_X86_TAG}.
     */
    public static final IdDisplay GOOGLE_APIS_TAG = IdDisplay.create("google_apis", "Google APIs");

    /**
     * A separate tag to apply to system images that include Google APIs on x86 systems.
     * Note this tag was used for api 19 and has been used since.
     */
    public static final IdDisplay GOOGLE_APIS_X86_TAG = IdDisplay.create("google_apis_x86", "Google APIs x86");

    /**
     * Directory containing the system image.
     */
    private final File mLocation;

    /**
     * Tag of the system image. Used for matching addons and system images, and for filtering.
     */
    private final IdDisplay mTag;

    /**
     * Vendor of the system image.
     */
    private final IdDisplay mVendor;


    /**
     * Abi (x86, armeabi-v7a, etc) of the system image.
     */
    private final String mAbi;

    /**
     * Skins contained in this system image, or in the platform/addon it's based on.
     */
    private final File[] mSkins;

    /**
     * Android API level of this system image.
     */
    private final AndroidVersion mAndroidVersion;

    /**
     * {@link RepoPackage} that contains this system image.
     */
    private final RepoPackage mPackage;

    public SystemImage(@NonNull File location, @Nullable IdDisplay tag, @Nullable IdDisplay vendor,
            @NonNull String abi, @NonNull File[] skins,
            @NonNull RepoPackage pkg) {
        mLocation = location;
        mTag = tag;
        mVendor = vendor;
        mAbi = abi;
        mSkins = skins;
        mPackage = pkg;
        TypeDetails details = pkg.getTypeDetails();
        assert details instanceof DetailsTypes.ApiDetailsType;
        mAndroidVersion = DetailsTypes.getAndroidVersion((DetailsTypes.ApiDetailsType) details);
    }

    @NonNull
    @Override
    public File getLocation() {
        return mLocation;
    }

    @NonNull
    @Override
    public IdDisplay getTag() {
        return mTag;
    }

    @Nullable
    @Override
    public IdDisplay getAddonVendor() {
        return mVendor;
    }

    @NonNull
    @Override
    public String getAbiType() {
        return mAbi;
    }

    @NonNull
    @Override
    public File[] getSkins() {
        return mSkins;
    }

    @Override
    @NonNull
    public AndroidVersion getAndroidVersion() {
        return mAndroidVersion;
    }

    @NonNull
    public RepoPackage getPackage() {
        return mPackage;
    }

    @Override
    public boolean obsolete() {
        return mPackage.obsolete();
    }

    @Override
    public int compareTo(ISystemImage o) {
        int res = getTag().compareTo(o.getTag());
        if (res != 0) {
            return res;
        }
        res = getAbiType().compareTo(o.getAbiType());
        if (res != 0) {
            return res;
        }
        if (getAddonVendor() == null ^ o.getAddonVendor() != null) {
            return getAddonVendor() == null ? -1 : 1;
        }
        if (getAddonVendor() != null && o.getAddonVendor() != null) {
            res = getAddonVendor().compareTo(o.getAddonVendor());
            if (res != 0) {
                return res;
            }
        }
        res = getLocation().compareTo(o.getLocation());
        if (res != 0) {
            return res;
        }
        File[] skins = getSkins();
        File[] otherSkins = o.getSkins();
        for (int i = 0; i < skins.length && i < otherSkins.length; i++) {
            res = skins[i].compareTo(otherSkins[i]);
            if (res != 0) {
                return res;
            }
        }
        return skins.length - otherSkins.length;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SystemImage)) {
            return false;
        }
        return compareTo((SystemImage) o) == 0;
    }

    public int hashCode() {
        int hashCode = Objects.hashCode(getTag(), getAbiType(), getAddonVendor(), getLocation());
        for (File f : getSkins()) {
            hashCode *= 37;
            hashCode += f.hashCode();
        }
        return hashCode;
    }

    @NonNull
    @Override
    public Revision getRevision() {
        return mPackage.getVersion();
    }
}
