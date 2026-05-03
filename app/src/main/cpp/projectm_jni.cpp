#include <jni.h>
#include <android/log.h>
#include <string>
#include <fstream>
#include <sstream>
#include <cmath>
#include <atomic>
#include <mutex>
#include <unordered_map>
#include <cstdint>

#define LOG_TAG "MilkDropBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Include projectM header if the submodule is present
#if __has_include("projectm/src/api/include/projectM-4/projectM.h")
#  include "projectm/src/api/include/projectM-4/projectM.h"
#  define PROJECTM_AVAILABLE 1
#elif __has_include("projectM-4/projectM.h")
#  include "projectM-4/projectM.h"
#  define PROJECTM_AVAILABLE 1
#else
#  define PROJECTM_AVAILABLE 0
   // Stub types so the rest of the file compiles without the real header
   typedef void* projectm_handle;
   typedef enum { PROJECTM_MONO = 1, PROJECTM_STEREO = 2 } projectm_channels;
   static inline projectm_handle projectm_create() { return nullptr; }
   static inline void projectm_destroy(projectm_handle) {}
   static inline void projectm_render_frame(projectm_handle) {}
   static inline void projectm_set_window_size(projectm_handle, size_t, size_t) {}
   static inline void projectm_pcm_add_int16(projectm_handle, const int16_t*, uint32_t, projectm_channels) {}
   static inline void projectm_load_preset_file(projectm_handle, const char*, bool) {}
   static inline void projectm_set_preset_duration(projectm_handle, double) {}
   static inline void projectm_set_soft_cut_duration(projectm_handle, double) {}
   static inline void projectm_set_beat_sensitivity(projectm_handle, float) {}
#endif

// ─── Handle map ──────────────────────────────────────────────────────────────
// Maps opaque jlong keys to projectm_handle pointers.
// All access is protected by g_handleMutex.

static std::mutex g_handleMutex;
static std::unordered_map<jlong, projectm_handle> g_handles;
static jlong g_nextKey = 1; // 0 is reserved for "invalid"

static jlong storeHandle(projectm_handle h) {
    std::lock_guard<std::mutex> lock(g_handleMutex);
    jlong key = g_nextKey++;
    g_handles[key] = h;
    return key;
}

static projectm_handle lookupHandle(jlong key) {
    std::lock_guard<std::mutex> lock(g_handleMutex);
    auto it = g_handles.find(key);
    if (it == g_handles.end()) return nullptr;
    return it->second;
}

static void removeHandle(jlong key) {
    std::lock_guard<std::mutex> lock(g_handleMutex);
    g_handles.erase(key);
}

// ─── Bass / treble energy ─────────────────────────────────────────────────────
// Maintained as exponential moving averages updated on every feedAudio call.
// Reads from getBass/getTreble are lock-free via std::atomic.

static std::atomic<float> g_bassEnergy{0.0f};
static std::atomic<float> g_trebleEnergy{0.0f};

// Compute RMS of a slice of int16 samples (interleaved, any channel count).
// startSample and endSample are frame indices (not byte offsets).
static float computeRms(const int16_t* samples, int startSample, int endSample, int channels) {
    if (endSample <= startSample) return 0.0f;
    double sumSq = 0.0;
    int count = 0;
    for (int i = startSample; i < endSample; ++i) {
        for (int c = 0; c < channels; ++c) {
            float s = static_cast<float>(samples[i * channels + c]) / 32768.0f;
            sumSq += static_cast<double>(s * s);
            ++count;
        }
    }
    if (count == 0) return 0.0f;
    return static_cast<float>(std::sqrt(sumSq / count));
}

static void updateEnergyMetrics(const int16_t* samples, int frameCount, int channels) {
    if (frameCount <= 0) return;

    // Bass: first quarter of frames (low-frequency content dominates early in time)
    int bassEnd   = frameCount / 4;
    // Treble: last quarter of frames
    int trebleStart = frameCount - frameCount / 4;

    float newBass   = computeRms(samples, 0,           bassEnd,    channels);
    float newTreble = computeRms(samples, trebleStart, frameCount, channels);

    // Exponential moving average: energy = 0.7 * energy + 0.3 * newRms
    float prevBass   = g_bassEnergy.load(std::memory_order_relaxed);
    float prevTreble = g_trebleEnergy.load(std::memory_order_relaxed);
    g_bassEnergy.store(0.7f * prevBass   + 0.3f * newBass,   std::memory_order_relaxed);
    g_trebleEnergy.store(0.7f * prevTreble + 0.3f * newTreble, std::memory_order_relaxed);
}

// ─── JNI implementations ──────────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_milkdrop_ProjectMBridge_create(
        JNIEnv* env, jobject /* this */, jint width, jint height, jstring presetPath) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = projectm_create();
    if (!pm) {
        LOGE("projectm_create() returned null");
        return 0L;
    }
    projectm_set_window_size(pm,
        static_cast<size_t>(width),
        static_cast<size_t>(height));

    // Optionally load the initial preset directory path (informational log only;
    // actual preset loading is done via loadPresetNative).
    const char* path = env->GetStringUTFChars(presetPath, nullptr);
    LOGI("projectM created: size=%dx%d, presetPath=%s", width, height, path ? path : "(null)");
    env->ReleaseStringUTFChars(presetPath, path);

    jlong key = storeHandle(pm);
    return key;
#else
    (void)env; (void)width; (void)height; (void)presetPath;
    LOGI("projectM stub: create called (submodule not yet populated)");
    return 0L;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_milkdrop_ProjectMBridge_renderFrameNative(
        JNIEnv*, jobject, jlong handle) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = lookupHandle(handle);
    if (pm) projectm_render_frame(pm);
#else
    (void)handle;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_milkdrop_ProjectMBridge_feedAudioNative(
        JNIEnv* env, jobject, jlong handle, jshortArray pcmData, jint channels) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = lookupHandle(handle);
    if (!pm) return;

    jsize totalSamples = env->GetArrayLength(pcmData);
    if (totalSamples <= 0) return;

    jshort* buf = env->GetShortArrayElements(pcmData, nullptr);
    if (!buf) return;

    int ch = (channels == 2) ? 2 : 1;
    int frameCount = static_cast<int>(totalSamples) / ch;

    projectm_pcm_add_int16(
        pm,
        reinterpret_cast<const int16_t*>(buf),
        static_cast<uint32_t>(frameCount),
        ch == 2 ? PROJECTM_STEREO : PROJECTM_MONO);

    // Update bass/treble energy metrics from this PCM buffer
    updateEnergyMetrics(reinterpret_cast<const int16_t*>(buf), frameCount, ch);

    env->ReleaseShortArrayElements(pcmData, buf, JNI_ABORT);
#else
    (void)env; (void)handle; (void)pcmData; (void)channels;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_milkdrop_ProjectMBridge_loadPresetNative(
        JNIEnv* env, jobject, jlong handle, jstring presetPath, jboolean smooth) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = lookupHandle(handle);
    if (!pm) return;

    const char* path = env->GetStringUTFChars(presetPath, nullptr);
    if (path) {
        projectm_load_preset_file(pm, path, smooth == JNI_TRUE);
        LOGI("Loaded preset: %s (smooth=%d)", path, smooth == JNI_TRUE ? 1 : 0);
        env->ReleaseStringUTFChars(presetPath, path);
    }
#else
    (void)env; (void)handle; (void)presetPath; (void)smooth;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_milkdrop_ProjectMBridge_parsePreset(
        JNIEnv* env, jobject, jstring presetPath) {
    const char* path = env->GetStringUTFChars(presetPath, nullptr);
    if (!path) {
        return env->NewStringUTF("ERROR: Failed to get preset path string");
    }

    std::string pathStr(path);
    env->ReleaseStringUTFChars(presetPath, path);

    // Check file existence
    std::ifstream file(pathStr, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        std::string err = "ERROR: File not found: " + pathStr;
        return env->NewStringUTF(err.c_str());
    }

    // Check file size (max 512 KB)
    std::streamsize fileSize = file.tellg();
    const std::streamsize kMaxSize = 512 * 1024; // 512 KB
    if (fileSize > kMaxSize) {
        file.close();
        return env->NewStringUTF("ERROR: File too large");
    }

    // Read up to 4 KB for header validation
    file.seekg(0, std::ios::beg);
    const std::streamsize kReadSize = 4096;
    std::streamsize readSize = (fileSize < kReadSize) ? fileSize : kReadSize;
    std::string header(static_cast<size_t>(readSize), '\0');
    file.read(&header[0], readSize);
    file.close();

    // Validate presence of known MilkDrop preset markers
    bool hasPreset00       = header.find("[preset00]")              != std::string::npos;
    bool hasMilkdropHeader = header.find("[milkdrop preset]")       != std::string::npos;
    bool hasFRating        = header.find("fRating")                 != std::string::npos;
    bool hasVersionKey     = header.find("MILKDROP_PRESET_VERSION") != std::string::npos;

    if (!hasPreset00 && !hasMilkdropHeader && !hasFRating && !hasVersionKey) {
        return env->NewStringUTF("ERROR: Missing preset header section");
    }

    return env->NewStringUTF("OK");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_milkdrop_ProjectMBridge_destroyNative(
        JNIEnv*, jobject, jlong handle) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = lookupHandle(handle);
    if (pm) {
        projectm_destroy(pm);
        removeHandle(handle);
        LOGI("projectM destroyed (handle=%lld)", static_cast<long long>(handle));
    }
#else
    (void)handle;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_milkdrop_ProjectMBridge_reinitialize(
        JNIEnv*, jobject, jlong handle, jint width, jint height) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = lookupHandle(handle);
    if (pm) {
        projectm_set_window_size(pm,
            static_cast<size_t>(width),
            static_cast<size_t>(height));
        LOGI("projectM reinitialized: %dx%d", width, height);
    }
#else
    (void)handle; (void)width; (void)height;
    LOGI("reinitialize stub: %dx%d", width, height);
#endif
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_milkdrop_ProjectMBridge_getBassNative(
        JNIEnv*, jobject, jlong handle) {
    (void)handle; // energy is global, not per-handle
    return g_bassEnergy.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_milkdrop_ProjectMBridge_getTrebleNative(
        JNIEnv*, jobject, jlong handle) {
    (void)handle; // energy is global, not per-handle
    return g_trebleEnergy.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_milkdrop_ProjectMBridge_setPresetDuration(
        JNIEnv*, jobject, jlong handle, jdouble seconds) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = lookupHandle(handle);
    if (pm) {
        projectm_set_preset_duration(pm, static_cast<double>(seconds));
        LOGI("setPresetDuration: %.1f s", static_cast<double>(seconds));
    }
#else
    (void)handle; (void)seconds;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_milkdrop_ProjectMBridge_setSoftCutDuration(
        JNIEnv*, jobject, jlong handle, jdouble seconds) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = lookupHandle(handle);
    if (pm) {
        projectm_set_soft_cut_duration(pm, static_cast<double>(seconds));
        LOGI("setSoftCutDuration: %.1f s", static_cast<double>(seconds));
    }
#else
    (void)handle; (void)seconds;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_milkdrop_ProjectMBridge_setBeatSensitivity(
        JNIEnv*, jobject, jlong handle, jfloat sensitivity) {
#if PROJECTM_AVAILABLE
    projectm_handle pm = lookupHandle(handle);
    if (pm) {
        projectm_set_beat_sensitivity(pm, static_cast<float>(sensitivity));
        LOGI("setBeatSensitivity: %.2f", static_cast<double>(sensitivity));
    }
#else
    (void)handle; (void)sensitivity;
#endif
}
