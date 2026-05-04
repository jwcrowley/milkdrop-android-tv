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
    -O3 -fvisibility=hidden -fstack-protector-strong -std=c++17
    -ffast-math -march=armv8-a+simd
)

if(PROJECTM_AVAILABLE)
    # The actual CMake target is "projectM" (not "libprojectM")
    # "libprojectM::projectM" is an alias defined in projectM's own CMakeLists
    target_link_libraries(milkdrop_bridge
        libprojectM::projectM
        EGL
        GLESv3
        android
        log
    )
else()
    target_link_libraries(milkdrop_bridge
        EGL
        GLESv3
        android
        log
    )
endif()
