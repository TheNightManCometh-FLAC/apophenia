#include <jni.h>
#include <oboe/Oboe.h>

// JNI entry points for com.betterappmafia.apophenia.audio.AudioEngine.
// Called from Kotlin on non-audio threads only; nothing here runs in an audio callback.

extern "C" JNIEXPORT jstring JNICALL
Java_com_betterappmafia_apophenia_audio_AudioEngine_nativeOboeVersion(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(oboe::getVersionText());
}
