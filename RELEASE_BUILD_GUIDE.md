# Release APK Build Guide

This guide explains how to generate signed release APK builds for Shadow Master.

## Prerequisites

You need:
1. Android Studio or Gradle command line tools
2. A keystore file for signing the APK
3. The keystore credentials (passwords and key alias)

## Step 1: Create a Keystore (if you don't have one)

You can create a keystore using Android Studio or the command line:

### Using Android Studio
1. Go to **Build** → **Generate Signed Bundle/APK**
2. Select **APK**
3. Click **Create new...**
4. Fill in the keystore details:
   - **Key store path**: Choose a location outside the project (e.g., `~/keystores/shadowmaster.jks`)
   - **Password**: Choose a strong password for the keystore
   - **Alias**: A name for the key (e.g., `shadowmaster-key`)
   - **Key password**: Choose a strong password for the key
   - **Validity**: 25 years (recommended for Android apps)
   - **Certificate details**: Fill in your information

### Using Command Line
```bash
keytool -genkey -v -keystore ~/keystores/shadowmaster.jks \
  -alias shadowmaster-key -keyalg RSA -keysize 2048 -validity 10000
```

**Important:** Keep your keystore file and passwords secure! If you lose them, you cannot update your app on Google Play.

## Step 2: Configure Signing

1. Copy the keystore properties template:
   ```bash
   cp keystore.properties.template keystore.properties
   ```

2. Edit `keystore.properties` with your actual values:
   ```properties
   storeFile=/path/to/your/keystore.jks
   storePassword=your_keystore_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```

   **Note:** The `keystore.properties` file is gitignored and will not be committed to version control.

## Step 3: Build Release APK

### Using Android Studio
1. Go to **Build** → **Select Build Variant**
2. Change **Active Build Variant** from `debug` to `release`
3. Go to **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
4. Wait for the build to complete
5. Click **locate** in the notification to find the APK

### Using Gradle Command Line
```bash
# Clean build
./gradlew clean

# Build release APK
./gradlew assembleRelease

# The APK will be at:
# app/build/outputs/apk/release/app-release.apk
```

## Step 4: Verify the APK

Before distributing, verify the APK is properly signed:

```bash
# Check APK signature
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk

# View signing certificate
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

## Release Build Configuration

The release build is configured with:
- **Code shrinking**: Enabled (ProGuard/R8)
- **Code obfuscation**: Enabled via ProGuard rules
- **Optimization**: Enabled
- **ProGuard rules**: See `app/proguard-rules.pro`

Important classes preserved:
- Azure Speech SDK
- Silero VAD
- Vosk transcription
- JNA (Java Native Access)
- Hilt dependency injection
- Data classes for Room/DataStore

## Troubleshooting

### keystore.properties not configured
If you haven't created `keystore.properties`, the build will proceed without signing configuration and produce an unsigned APK. This is normal and expected. To create a signed APK, follow Step 2 to configure signing.

### App crashes after installing release APK
Check ProGuard rules in `app/proguard-rules.pro`. You may need to add `-keep` rules for classes that are accessed via reflection.

### "Invalid keystore format"
Make sure your keystore file path in `keystore.properties` is correct and the file exists.

## Security Best Practices

1. **Never commit** keystore files or passwords to version control
2. **Backup** your keystore file securely (you cannot recover it if lost)
3. **Use different keystores** for different apps
4. **Keep passwords** in a secure password manager
5. **Restrict access** to keystore files (only authorized developers)

## Distribution

Once you have a signed release APK, you can:
- **Google Play Store**: Upload to Play Console
- **Direct distribution**: Share the APK file directly
- **GitHub Releases**: Attach to a GitHub release
- **F-Droid**: Submit to F-Droid repository

For Google Play Store, consider using Android App Bundles (AAB) instead:
```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

## Version Management

Before building a release:
1. Update `versionCode` in `app/build.gradle.kts` (increment by 1)
2. Update `versionName` in `app/build.gradle.kts` (e.g., "1.0.0" → "1.0.1")
3. Update changelog/release notes

## Related Files

- `app/build.gradle.kts` - Build configuration with signing setup
- `app/proguard-rules.pro` - ProGuard rules for release builds
- `keystore.properties.template` - Template for keystore configuration
- `.gitignore` - Ensures keystore files are not committed
