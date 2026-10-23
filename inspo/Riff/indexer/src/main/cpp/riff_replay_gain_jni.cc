#include <jni.h>

#include <cmath>
#include <cstdint>
#include <vector>

extern "C" {
#include "third_party/libebur128/ebur128.h"
}

namespace {
struct Analyzer {
    ebur128_state* state;
    unsigned channels;
    std::vector<int> pcm24_scratch;
};

void Throw(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalArgumentException");
    if (type != nullptr) env->ThrowNew(type, message);
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_link_roadio_riff_indexer_android_replaygain_ReplayGainNativeAnalyzer_nativeCreate(
    JNIEnv* env, jobject, jint channels, jint sample_rate) {
    if (channels <= 0 || sample_rate <= 0) {
        Throw(env, "Invalid loudness stream format");
        return 0;
    }
    ebur128_state* state = ebur128_init(
        static_cast<unsigned>(channels), static_cast<unsigned long>(sample_rate),
        EBUR128_MODE_I);
    if (state == nullptr) {
        Throw(env, "Could not create loudness analyzer");
        return 0;
    }
    return reinterpret_cast<jlong>(new Analyzer{state, static_cast<unsigned>(channels), {}});
}

extern "C" JNIEXPORT void JNICALL
Java_link_roadio_riff_indexer_android_replaygain_ReplayGainNativeAnalyzer_nativeAdd(
    JNIEnv* env, jobject, jlong handle, jfloatArray samples, jint frames) {
    auto* analyzer = reinterpret_cast<Analyzer*>(handle);
    if (analyzer == nullptr || samples == nullptr || frames < 0) {
        Throw(env, "Invalid loudness analyzer input");
        return;
    }
    jfloat* values = env->GetFloatArrayElements(samples, nullptr);
    if (values == nullptr) return;
    const int result = ebur128_add_frames_float(
        analyzer->state, values, static_cast<size_t>(frames));
    env->ReleaseFloatArrayElements(samples, values, JNI_ABORT);
    if (result != EBUR128_SUCCESS) Throw(env, "Loudness analyzer rejected PCM");
}

extern "C" JNIEXPORT void JNICALL
Java_link_roadio_riff_indexer_android_replaygain_ReplayGainNativeAnalyzer_nativeAddPcm(
    JNIEnv* env, jobject, jlong handle, jobject buffer, jint byte_offset,
    jint frames, jint pcm_encoding) {
    auto* analyzer = reinterpret_cast<Analyzer*>(handle);
    auto* bytes = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (analyzer == nullptr || bytes == nullptr || capacity < 0 ||
        byte_offset < 0 || frames < 0) {
        Throw(env, "Invalid direct loudness PCM input");
        return;
    }
    size_t bytes_per_sample = 0;
    switch (pcm_encoding) {
        case 2: bytes_per_sample = 2; break;
        case 4: bytes_per_sample = 4; break;
        case 21: bytes_per_sample = 3; break;
        case 22: bytes_per_sample = 4; break;
        default:
            Throw(env, "Unsupported direct loudness PCM encoding");
            return;
    }
    const size_t sample_count = static_cast<size_t>(frames) * analyzer->channels;
    const size_t byte_count = sample_count * bytes_per_sample;
    if (static_cast<size_t>(byte_offset) + byte_count > static_cast<size_t>(capacity)) {
        Throw(env, "Direct loudness PCM range is invalid");
        return;
    }
    const uint8_t* source = bytes + byte_offset;
    int result;
    if (pcm_encoding == 2) {
        result = ebur128_add_frames_short(
            analyzer->state,
            reinterpret_cast<const short*>(source),
            static_cast<size_t>(frames));
    } else if (pcm_encoding == 4) {
        result = ebur128_add_frames_float(
            analyzer->state,
            reinterpret_cast<const float*>(source),
            static_cast<size_t>(frames));
    } else if (pcm_encoding == 22) {
        result = ebur128_add_frames_int(
            analyzer->state,
            reinterpret_cast<const int*>(source),
            static_cast<size_t>(frames));
    } else {
        analyzer->pcm24_scratch.resize(sample_count);
        for (size_t index = 0; index < sample_count; ++index) {
            const size_t offset = index * 3;
            int sample =
                static_cast<int>(source[offset]) |
                (static_cast<int>(source[offset + 1]) << 8) |
                (static_cast<int>(source[offset + 2]) << 16);
            if ((sample & 0x00800000) != 0) sample |= ~0x00ffffff;
            analyzer->pcm24_scratch[index] = sample * 256;
        }
        result = ebur128_add_frames_int(
            analyzer->state,
            analyzer->pcm24_scratch.data(),
            static_cast<size_t>(frames));
    }
    if (result != EBUR128_SUCCESS) {
        Throw(env, "Loudness analyzer rejected direct PCM");
    }
}

extern "C" JNIEXPORT jdouble JNICALL
Java_link_roadio_riff_indexer_android_replaygain_ReplayGainNativeAnalyzer_nativeFinish(
    JNIEnv* env, jobject, jlong handle) {
    auto* analyzer = reinterpret_cast<Analyzer*>(handle);
    if (analyzer == nullptr) {
        Throw(env, "Invalid loudness analyzer state");
        return 0.0;
    }
    double loudness = 0.0;
    if (ebur128_loudness_global(analyzer->state, &loudness) != EBUR128_SUCCESS ||
        !std::isfinite(loudness)) {
        Throw(env, "Integrated loudness is unavailable");
        return 0.0;
    }
    return loudness;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_link_roadio_riff_indexer_android_replaygain_ReplayGainNativeAnalyzer_nativeFinishMultiple(
    JNIEnv* env, jobject, jlongArray handles) {
    if (handles == nullptr) {
        Throw(env, "Loudness analyzer list is missing");
        return 0.0;
    }
    const jsize count = env->GetArrayLength(handles);
    if (count <= 0) {
        Throw(env, "Loudness analyzer list is empty");
        return 0.0;
    }
    jlong* values = env->GetLongArrayElements(handles, nullptr);
    if (values == nullptr) return 0.0;
    std::vector<ebur128_state*> states(static_cast<size_t>(count));
    bool valid = true;
    for (jsize index = 0; index < count; ++index) {
        auto* analyzer = reinterpret_cast<Analyzer*>(values[index]);
        valid = valid && analyzer != nullptr;
        states[static_cast<size_t>(index)] = analyzer == nullptr ? nullptr : analyzer->state;
    }
    env->ReleaseLongArrayElements(handles, values, JNI_ABORT);
    if (!valid) {
        Throw(env, "Invalid loudness analyzer state list");
        return 0.0;
    }
    double loudness = 0.0;
    if (ebur128_loudness_global_multiple(states.data(), states.size(), &loudness) !=
            EBUR128_SUCCESS ||
        !std::isfinite(loudness)) {
        Throw(env, "Integrated loudness is unavailable");
        return 0.0;
    }
    return loudness;
}

extern "C" JNIEXPORT void JNICALL
Java_link_roadio_riff_indexer_android_replaygain_ReplayGainNativeAnalyzer_nativeDestroy(
    JNIEnv*, jobject, jlong handle) {
    auto* analyzer = reinterpret_cast<Analyzer*>(handle);
    if (analyzer != nullptr) {
        ebur128_destroy(&analyzer->state);
        delete analyzer;
    }
}
