#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <complex>
#include <cstddef>
#include <vector>

namespace {

constexpr int kExcerptCount = 3;
constexpr int kSampleRate = 32000;
constexpr int kExcerptSamples = 320000;
constexpr int kPreemphasizedSamples = kExcerptSamples - 1;
constexpr int kFftSize = 1024;
constexpr int kFftBins = kFftSize / 2 + 1;
constexpr int kHopSize = 320;
constexpr int kWindowSize = 800;
constexpr int kWindowOffset = (kFftSize - kWindowSize) / 2;
constexpr int kFrames = 1000;
constexpr int kMelBins = 64;
constexpr float kFmin = 0.0F;
constexpr float kFmax = 15000.0F;
constexpr float kPreemphasis = 0.97F;
constexpr float kLogOffset = 1.0e-5F;
constexpr float kPi = 3.14159265358979323846F;

int ReflectIndex(int index, int size) {
    while (index < 0 || index >= size) {
        index = index < 0 ? -index : 2 * size - 2 - index;
    }
    return index;
}

float HertzToMel(float frequency) {
    return 1127.0F * std::log(1.0F + frequency / 700.0F);
}

struct FrontendTables {
    std::array<float, kWindowSize> window{};
    std::array<int, kFftSize> bit_reversal{};
    std::array<std::complex<float>, kFftSize / 2> roots{};
    std::array<float, kMelBins * kFftBins> mel{};

    FrontendTables() {
        for (int index = 0; index < kWindowSize; ++index) {
            window[index] =
                0.5F - 0.5F *
                           std::cos(2.0F * kPi * index / (kWindowSize - 1));
        }
        for (int index = 0; index < kFftSize; ++index) {
            int value = index;
            int reversed = 0;
            for (int bit = 0; bit < 10; ++bit) {
                reversed = (reversed << 1) | (value & 1);
                value >>= 1;
            }
            bit_reversal[index] = reversed;
        }
        for (int index = 0; index < kFftSize / 2; ++index) {
            const float angle = -2.0F * kPi * index / kFftSize;
            roots[index] = {std::cos(angle), std::sin(angle)};
        }

        const float mel_min = HertzToMel(kFmin);
        const float mel_max = HertzToMel(kFmax);
        const float mel_delta = (mel_max - mel_min) / (kMelBins + 1);
        for (int band = 0; band < kMelBins; ++band) {
            const float lower = mel_min + band * mel_delta;
            const float center = lower + mel_delta;
            const float upper = center + mel_delta;
            // Kaldi returns n_fft / 2 bins. EfficientAT pads one zero
            // weight for the Nyquist bin before matrix multiplication.
            for (int bin = 0; bin < kFftSize / 2; ++bin) {
                const float frequency =
                    static_cast<float>(bin * kSampleRate) / kFftSize;
                const float mel_frequency = HertzToMel(frequency);
                const float rising =
                    (mel_frequency - lower) / (center - lower);
                const float falling =
                    (upper - mel_frequency) / (upper - center);
                mel[band * kFftBins + bin] =
                    std::max(0.0F, std::min(rising, falling));
            }
        }
    }
};

const FrontendTables& Tables() {
    static const FrontendTables tables;
    return tables;
}

void Transform(std::array<std::complex<float>, kFftSize>* values) {
    const auto& tables = Tables();
    for (int index = 0; index < kFftSize; ++index) {
        const int reversed = tables.bit_reversal[index];
        if (reversed > index) {
            std::swap((*values)[index], (*values)[reversed]);
        }
    }
    for (int length = 2; length <= kFftSize; length <<= 1) {
        const int half = length / 2;
        const int root_step = kFftSize / length;
        for (int start = 0; start < kFftSize; start += length) {
            for (int index = 0; index < half; ++index) {
                const auto product =
                    (*values)[start + index + half] *
                    tables.roots[index * root_step];
                const auto left = (*values)[start + index];
                (*values)[start + index] = left + product;
                (*values)[start + index + half] = left - product;
            }
        }
    }
}

void ComputeExcerpt(const float* pcm, float* output) {
    const auto& tables = Tables();
    std::array<std::complex<float>, kFftSize> spectrum{};
    std::array<float, kFftBins> power{};
    for (int frame = 0; frame < kFrames; ++frame) {
        spectrum.fill({0.0F, 0.0F});
        const int source_start =
            frame * kHopSize - kFftSize / 2 + kWindowOffset;
        for (int index = 0; index < kWindowSize; ++index) {
            const int source = ReflectIndex(
                source_start + index, kPreemphasizedSamples);
            const float sample = pcm[source + 1] - kPreemphasis * pcm[source];
            spectrum[kWindowOffset + index] = {
                sample * tables.window[index], 0.0F};
        }
        Transform(&spectrum);
        for (int bin = 0; bin < kFftBins; ++bin) {
            power[bin] = std::norm(spectrum[bin]);
        }
        for (int band = 0; band < kMelBins; ++band) {
            float energy = 0.0F;
            const float* weights = &tables.mel[band * kFftBins];
            for (int bin = 0; bin < kFftBins; ++bin) {
                energy += power[bin] * weights[bin];
            }
            output[frame * kMelBins + band] =
                (std::log(energy + kLogOffset) + 4.5F) / 5.0F;
        }
    }
}

void ThrowIllegalArgument(JNIEnv* environment, const char* message) {
    jclass type = environment->FindClass("java/lang/IllegalArgumentException");
    if (type != nullptr) {
        environment->ThrowNew(type, message);
    }
}

}  // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_link_roadio_riff_indexer_android_level2_Level2AudioFeatures_nativeComputeLogMel(
    JNIEnv* environment,
    jobject,
    jfloatArray pcm_array) {
    if (pcm_array == nullptr ||
        environment->GetArrayLength(pcm_array) !=
            kExcerptCount * kExcerptSamples) {
        ThrowIllegalArgument(
            environment,
            "Level2 PCM must contain exactly three 10-second 32-kHz excerpts");
        return nullptr;
    }
    jfloat* pcm = environment->GetFloatArrayElements(pcm_array, nullptr);
    if (pcm == nullptr) {
        return nullptr;
    }
    std::vector<float> result(kExcerptCount * kFrames * kMelBins);
    for (int excerpt = 0; excerpt < kExcerptCount; ++excerpt) {
        ComputeExcerpt(
            pcm + excerpt * kExcerptSamples,
            result.data() + excerpt * kFrames * kMelBins);
    }
    environment->ReleaseFloatArrayElements(pcm_array, pcm, JNI_ABORT);

    jfloatArray output =
        environment->NewFloatArray(static_cast<jsize>(result.size()));
    if (output != nullptr) {
        environment->SetFloatArrayRegion(
            output,
            0,
            static_cast<jsize>(result.size()),
            result.data());
    }
    return output;
}
