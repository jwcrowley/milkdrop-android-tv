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

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/projectm/CMakeLists.txt")
    add_subdirectory(projectm)

    # projectM 4.x add_subdirectory() exposes the target as "libprojectM".
    # Create the namespaced alias our JNI bridge expects.
    if(TARGET libprojectM AND NOT TARGET projectM::projectM)
        add_library(projectM::projectM ALIAS libprojectM)
    endif()
else()
    message(WARNING "projectM submodule not found — building stub")
    add_library(projectM_stub INTERFACE)
    add_library(projectM::projectM ALIAS projectM_stub)
endif()

# ─── JNI bridge ───────────────────────────────────────────────────────────────
add_library(milkdrop_bridge SHARED projectm_jni.cpp)

target_compile_options(milkdrop_bridge PRIVATE
    -O2 -fvisibility=hidden -fstack-protector-strong -std=c++17
)

target_link_libraries(milkdrop_bridge
    projectM::projectM
    EGL
    GLESv3
    android
    log
)
