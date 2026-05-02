cmake_minimum_required(VERSION 3.22.1)
project(milkdrop_bridge)

# ─── projectM configuration ───────────────────────────────────────────────────
# Disable all optional projectM components that are not needed on Android
set(ENABLE_SDL_UI          OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING         OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)   # static-link into our .so
set(PROJECTM_BUILD_TESTS   OFF CACHE BOOL "" FORCE)
set(ENABLE_GLES            ON  CACHE BOOL "" FORCE)   # OpenGL ES mode
set(ENABLE_OPENMP          OFF CACHE BOOL "" FORCE)
set(ENABLE_LLVM            OFF CACHE BOOL "" FORCE)

# Add projectM as a subdirectory (populated by git submodule)
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/projectm/CMakeLists.txt")
    add_subdirectory(projectm)
else()
    message(WARNING "projectM submodule not found at ${CMAKE_CURRENT_SOURCE_DIR}/projectm. "
                    "Run: git submodule update --init --recursive")
    # Create a stub target so the build doesn't fail before the submodule is populated
    add_library(projectM_stub INTERFACE)
    add_library(projectM::projectM ALIAS projectM_stub)
endif()

# ─── JNI bridge shared library ────────────────────────────────────────────────
add_library(milkdrop_bridge SHARED projectm_jni.cpp)

target_compile_options(milkdrop_bridge PRIVATE
    -O2
    -fvisibility=hidden
    -fstack-protector-strong
    -std=c++17
)

target_link_libraries(milkdrop_bridge
    projectM::projectM          # static libprojectM (or stub if submodule missing)
    EGL
    GLESv3
    android
    log
)
