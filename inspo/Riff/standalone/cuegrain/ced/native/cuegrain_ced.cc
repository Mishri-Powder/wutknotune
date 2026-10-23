// SPDX-License-Identifier: Apache-2.0
#include <jni.h>
#include <algorithm>
#include <string>
#include <vector>
#include "compact_enc_det/compact_enc_det.h"
#include "util/encodings/encodings.h"
#include "util/languages/languages.h"

extern "C" JNIEXPORT jobjectArray JNICALL
Java_link_roadio_cuegrain_ced_CedEncodingDetector_detectNative(
    JNIEnv* env, jobject, jbyteArray input) {
  const jsize length = env->GetArrayLength(input);
  if (length > 65536) {
    env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "CED input exceeds 65536 bytes");
    return nullptr;
  }
  std::vector<char> bytes(std::max(1, length));
  env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
  if (env->ExceptionCheck()) return nullptr;
  int consumed = 0;
  bool reliable = false;
  const Encoding encoding = CompactEncDet::DetectEncoding(
      bytes.data(), length, nullptr, nullptr, nullptr, UNKNOWN_ENCODING,
      UNKNOWN_LANGUAGE, CompactEncDet::QUERY_CORPUS, true, &consumed, &reliable);
  jobjectArray result = env->NewObjectArray(3, env->FindClass("java/lang/String"), nullptr);
  if (result == nullptr) return nullptr;
  const std::string values[] = {
      MimeEncodingName(encoding), reliable ? "true" : "false",
      std::to_string(std::max(0, std::min(consumed, length)))};
  for (int i = 0; i < 3; ++i) {
    jstring value = env->NewStringUTF(values[i].c_str());
    if (value == nullptr) return nullptr;
    env->SetObjectArrayElement(result, i, value);
    env->DeleteLocalRef(value);
  }
  return result;
}
