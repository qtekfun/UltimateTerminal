# SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Replaces upstream's Android.mk (ndk-build) with CMake; termux.c is unmodified.
cmake_minimum_required(VERSION 3.22)
project(termux C)

add_library(termux SHARED termux.c)
target_link_options(termux PRIVATE -Wl,--gc-sections)
