#include <jni.h>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ptrace.h>
#include <sys/types.h>

#define JNI_EXPORT __attribute__((visibility("default")))

extern "C" {

JNI_EXPORT jdouble JNICALL
Java_com_parfumecreator_core_nativebridge_NativeSecurityBridge_calculateSplNative(
    JNIEnv *env,
    jobject /* thiz */,
    jdouble concentrationPct,
    jdouble topRatio,
    jdouble middleRatio,
    jdouble baseRatio
) {
    if (!std::isfinite(concentrationPct) || concentrationPct <= 0.0) {
        return 0.0;
    }

    double safeTop = (std::isfinite(topRatio) && topRatio >= 0.0) ? topRatio : 0.0;
    double safeMid = (std::isfinite(middleRatio) && middleRatio >= 0.0) ? middleRatio : 0.0;
    double safeBase = (std::isfinite(baseRatio) && baseRatio >= 0.0) ? baseRatio : 0.0;

    double maxRatio = std::max({safeTop, safeMid, safeBase});
    if (maxRatio <= 0.0) {
        return 0.0;
    }

    double normTop = safeTop / maxRatio;
    double normMid = safeMid / maxRatio;
    double normBase = safeBase / maxRatio;
    double sumRatios = normTop + normMid + normBase;

    if (sumRatios <= 0.0) {
        return 0.0;
    }

    normTop /= sumRatios;
    normMid /= sumRatios;
    normBase /= sumRatios;

    // Masked weights computed in C++
    const double baseWeight = 1.5;
    const double midWeight = 1.0;
    const double topWeight = 0.5;
    const double divisor = 3.0;

    double noteWeightFactor = ((normBase * baseWeight) + (normMid * midWeight) + (normTop * topWeight)) / divisor;
    double spl = concentrationPct * noteWeightFactor;

    if (spl < 0.0) spl = 0.0;
    if (spl > 100.0) spl = 100.0;

    return spl;
}

JNI_EXPORT jdouble JNICALL
Java_com_parfumecreator_core_nativebridge_NativeSecurityBridge_calculateConcentrationNative(
    JNIEnv *env,
    jobject /* thiz */,
    jdouble fragranceWeightG,
    jdouble totalWeightG
) {
    if (!std::isfinite(fragranceWeightG) || !std::isfinite(totalWeightG) ||
        totalWeightG <= 0.0 || fragranceWeightG <= 0.0) {
        return 0.0;
    }

    double pct = (fragranceWeightG / totalWeightG) * 100.0;
    if (pct < 0.0) pct = 0.0;
    if (pct > 100.0) pct = 100.0;

    return pct;
}

JNI_EXPORT jboolean JNICALL
Java_com_parfumecreator_core_nativebridge_NativeSecurityBridge_checkNativeAntiDebug(
    JNIEnv *env,
    jobject /* thiz */
) {
    // 1. Check ptrace attachment
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) < 0) {
        // Failed to trace self: another process is already attached
        return JNI_TRUE;
    }

    // 2. Read /proc/self/status for TracerPid using POSIX syscalls
    int fd = open("/proc/self/status", O_RDONLY);
    if (fd >= 0) {
        char buffer[1024];
        ssize_t bytesRead = read(fd, buffer, sizeof(buffer) - 1);
        close(fd);

        if (bytesRead > 0) {
            buffer[bytesRead] = '\0';
            const char *tracerTag = "TracerPid:\t";
            char *pos = strstr(buffer, tracerTag);
            if (pos) {
                pos += strlen(tracerTag);
                while (*pos == ' ' || *pos == '\t') pos++;
                if (*pos >= '1' && *pos <= '9') {
                    return JNI_TRUE;
                }
            }
        }
    }

    return JNI_FALSE;
}

JNI_EXPORT jboolean JNICALL
Java_com_parfumecreator_core_nativebridge_NativeSecurityBridge_checkNativeHooking(
    JNIEnv *env,
    jobject /* thiz */
) {
    // Read /proc/self/maps using direct POSIX syscalls
    int fd = open("/proc/self/maps", O_RDONLY);
    if (fd < 0) {
        return JNI_FALSE;
    }

    char buffer[2048];
    ssize_t bytesRead;
    bool foundHook = false;

    while ((bytesRead = read(fd, buffer, sizeof(buffer) - 1)) > 0) {
        buffer[bytesRead] = '\0';
        if (strstr(buffer, "frida-agent") ||
            strstr(buffer, "frida-gadget") ||
            strstr(buffer, "libxposed") ||
            strstr(buffer, "substrate") ||
            strstr(buffer, "cydia")) {
            foundHook = true;
            break;
        }
    }

    close(fd);
    return foundHook ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
