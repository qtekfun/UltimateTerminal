# SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Replacement of proot's loader/loader-info.awk (which needs gawk's strtonum) using only CMake.
# Writes the offset between the loader's pokedata_workaround symbol and its _start symbol.
# Run as: cmake -DREADELF=... -DLOADER=... -DOUT=... -P loader-info.cmake

execute_process(COMMAND "${READELF}" -s "${LOADER}" OUTPUT_VARIABLE symbols RESULT_VARIABLE status)
if(NOT status EQUAL 0)
    message(FATAL_ERROR "readelf failed on ${LOADER}")
endif()

string(REGEX MATCH "[0-9]+: ([0-9a-fA-F]+) +[0-9]+ +[A-Z]+ +[A-Z]+ +[A-Z]+ +[A-Za-z0-9]+ +pokedata_workaround" _ "${symbols}")
set(pokedata "${CMAKE_MATCH_1}")
string(REGEX MATCH "[0-9]+: ([0-9a-fA-F]+) +[0-9]+ +[A-Z]+ +[A-Z]+ +[A-Z]+ +[A-Za-z0-9]+ +_start" _ "${symbols}")
set(start "${CMAKE_MATCH_1}")
if(pokedata STREQUAL "" OR start STREQUAL "")
    message(FATAL_ERROR "pokedata_workaround or _start not found in ${LOADER}")
endif()

math(EXPR offset "0x${pokedata} - 0x${start}")
file(WRITE "${OUT}" "#include <unistd.h>\nconst ssize_t offset_to_pokedata_workaround=${offset};\n")
