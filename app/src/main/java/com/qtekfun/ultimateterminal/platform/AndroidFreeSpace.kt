// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.os.StatFs
import java.nio.file.Path

/** Free space of the volume holding [path], from `statvfs` through `StatFs`. */
object AndroidFreeSpace : (Path) -> Long {
    override fun invoke(path: Path): Long = StatFs(path.toString()).availableBytes
}
