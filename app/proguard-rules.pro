# SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Project-specific R8 rules. Libraries in use ship their own consumer rules.

# Commons Compress names optional codecs the app does not ship (only gzip and plain tar are read).
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.apache.commons.compress.harmony.**
