cmake_minimum_required(VERSION 3.22.1)
project("contactless_fingerprint_native")

# Optional native path, per the spec's "C++ / JNI (Optional for high-speed
# OpenCV operations)" dependency note. Not wired into the default pipeline
# (which runs entirely through OpenCV's Kotlin/Java bindings) -- this
# target exists as the migration path if segmentation (currently the
# heaviest stage at ~85% of pipeline time in the Python prototype's
# profiling) needs to move to native code for latency headroom on
# lower-end hardware.

find_package(OpenCV REQUIRED)

add_library(
    contactless_native
    SHARED
    native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(
    contactless_native
    ${OpenCV_LIBS}
    ${log-lib}
)
