# SPDX-License-Identifier: MPL-2.0

# Shizuku constructs this service through reflection in its process.
-keep class com.myuverr.codecobo.platform.AssociationUserService {
    public <init>();
    public <init>(android.content.Context);
}

# Okio's optional nullness annotation is not a runtime dependency.
-dontwarn javax.annotation.Nullable

# Diagnostic stack filtering identifies application frames by package.
-keeppackagenames com.myuverr.codecobo.**
