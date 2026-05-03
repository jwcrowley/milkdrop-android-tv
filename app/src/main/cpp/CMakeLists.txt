cmake_minimum_required(VERSION 3.22.1)
project(milkdrop_bridge)

# ─── projectM configuration ───────────────────────────────────────────────────
set(ENABLE_SDL_UI          OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING         OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(PROJECTM_BUILD_TESTS   OFF CACHE BOOL "" FORCE)
set(ENABLE_GLES            ON  CACHE BOOL "" FORCE)
set(ENABLE_OPENMP          OFF CACHE BOOL "" FORCE)
set(ENABLE_LLVM            OFF CACHE BOOL "" FORCE)
set(ENABLE_PLAYLIST        OFF CACHE BOOL "" FORCE)

set(PROJECTM_AVAILABLE FALSE)

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/projectm/CMakeLists.txt")
    add_subdirectory(projectm)
    set(PROJECTM_AVAILABLE TRUE)
    message(STATUS "projectM submodule found — building with real projectM")
else()
    message(WARNING "projectM submodule not found — building stub")
endif()

# ─── JNI bridge ───────────────────────────────────────────────────────────────
add_library(milkdrop_bridge SHARED projectm_jni.cpp)

target_compile_options(milkdrop_bridge PRIVATE
    -O2 -fvisibility=hidden -fstack-protector-strong -std=c++17
)

if(PROJECTM_AVAILABLE)
    # projectM 4.x add_subdirectory() target is "libprojectM"
    # Use target_link_libraries — this propagates include dirs transitively,
    # including the build-dir generated headers like projectM_export.h
    target_link_libraries(milkdrop_bridge
        libprojectM
        EGL
        GLESv3
        android
        log
    )
    # Do NOT manually add include dirs — let CMake propagate them from libprojectM
else()
    target_link_libraries(milkdrop_bridge
        EGL
        GLESv3
        android
        log
    )
endif()
