cmake_minimum_required(VERSION 3.18)
project(cuegrain_ced LANGUAGES CXX)
if(NOT ANDROID)
    find_package(JNI REQUIRED COMPONENTS JVM)
endif()
set(CED_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../vendor/compact_enc_det)
add_library(cuegrain_ced SHARED
    cuegrain_ced.cc
    ${CED_ROOT}/compact_enc_det/compact_enc_det.cc
    ${CED_ROOT}/compact_enc_det/compact_enc_det_hint_code.cc
    ${CED_ROOT}/util/encodings/encodings.cc
    ${CED_ROOT}/util/languages/languages.cc)
target_compile_features(cuegrain_ced PRIVATE cxx_std_17)
target_include_directories(cuegrain_ced PRIVATE ${CED_ROOT} ${JNI_INCLUDE_DIRS})
set_target_properties(cuegrain_ced PROPERTIES CXX_VISIBILITY_PRESET hidden)
