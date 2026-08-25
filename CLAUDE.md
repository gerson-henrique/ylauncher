# ylauncher

## Releases

**Every release gets a git tag.** Steps, in order:

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Run the tests, then build the signed bundle:
   `./gradlew :app:testDebugUnitTest :app:bundleRelease`
   Output: `app/build/outputs/bundle/release/app-release.aab`. Signing reads `key.properties`
   (untracked — kept locally, not in the repo).
3. Commit as `Bump version to <versionName> (versionCode <code>)`.
4. Push `main`, then tag that commit and push the tag:
   `git tag v<versionName> && git push origin v<versionName>`
   Tags are lightweight and `v`-prefixed, e.g. `v1.8.3`. (`v1.8.0`–`v1.8.2` were never tagged.)

`app/build/` is wiped by `./gradlew clean` — upload the `.aab` to Play, or copy it somewhere
durable, before cleaning.

The mapping file and baseline profile are embedded in the bundle
(`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`), so Play deobfuscates
crash traces on its own — there is nothing to upload by hand.

## Building

Pin a JDK 21 for every Gradle invocation:

```
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew <task>
```

With `JAVA_HOME` unset the launcher picks up the machine-default JDK 26, which Gradle 8.11
rejects — it fails instantly with a bare `26.0.2` and no explanation. `local.properties` sets
`org.gradle.java.home` to a JDK 17 for the daemon; that does not cover the launcher JVM.

## Database

`favorite_apps.panelId` and `folder_apps.folderId` are real foreign keys. Never insert or
update through the raw Room methods — `FavoriteDao`/`FolderDao` expose transactional wrappers
that resolve the parent row first, because a write against a deleted panel or folder crashes
the launcher with `SQLITE_CONSTRAINT_FOREIGNKEY`. The `panels` table must never be empty; it is
seeded idempotently in `AppModule` on create, on destructive migration, and on every open.
