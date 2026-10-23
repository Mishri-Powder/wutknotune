cmake_minimum_required(VERSION 3.22.1)

project(riff_storage_native LANGUAGES C CXX)

add_library(riff_xxh3 SHARED riff_xxh3.c)
add_library(riff_level2_audio SHARED riff_level2_audio_jni.cc)
add_library(
    riff_replay_gain
    SHARED
    riff_replay_gain_jni.cc
    third_party/libebur128/ebur128.c
)

# The same JNI and detector sources serve the standalone library and Android consumer.
add_subdirectory(../../../../standalone/cuegrain/ced/native cuegrain-ced)

target_compile_features(riff_xxh3 PRIVATE c_std_11)
target_compile_features(riff_level2_audio PRIVATE cxx_std_17)
target_compile_features(riff_replay_gain PRIVATE c_std_11 cxx_std_17)
target_compile_options(
    riff_xxh3
    PRIVATE
    -fvisibility=hidden
    -Wall
    -Wextra
    -Werror
)

target_compile_options(
    riff_level2_audio
    PRIVATE
    -fvisibility=hidden
    -O3
    -Wall
    -Wextra
    -Werror
)

target_compile_options(
    riff_replay_gain
    PRIVATE
    -fvisibility=hidden
    -O3
    -Wall
    -Wextra
)

