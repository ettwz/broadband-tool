# Android DNS resolver client source

Frozen AOSP DNS resolver v10 plus netd event listener v1, under Apache-2.0. `manifest.json` records immutable source revisions and every upstream file checksum. Original snapshots are retained unchanged.

Run `python3 tools/generate_dns_aidl.py` after loading `tools/wsl-env.sh`; `--check` verifies the committed generated Java files. The generator relocates Java names into the application namespace and adds AIDL `@Descriptor` annotations to retain original Binder descriptors. The pinned Android AIDL compiler supplies transaction IDs, structured parcelable encoding, version and hash methods from the complete upstream schemas.

The application requires a matching service descriptor, a responsive resolver and remote interface version >= 10. This avoids depending on platform proxy methods that Android 14 removes during shrinking. Only `flushNetworkCache` is exposed through the application's Shizuku operation allowlist; generated configuration APIs are used by the separate debug fixture, which is excluded from release APKs.

Generated Java is checked in so ordinary Gradle builds do not require Soong. `tools/check.sh` verifies it against the frozen schemas and pinned AIDL compiler; the generator normalizes only the machine-specific compiler path in its header comment.
