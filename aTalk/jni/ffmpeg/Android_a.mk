# The static libraries are built based on the scripts in the following site but with ffmpeg 3.3.3
# https://github.com/IljaKosynkin/FFmpeg-Development-Kit/tree/master
# see => https://medium.com/@ilja.kosynkin/building-ffmpeg-for-android-607222677a9e

LOCAL_PATH := $(call my-dir)
LOCAL_LIB_PATH = android/$(TARGET_ARCH_ABI)
PATH_X264:=../x264/android/$(TARGET_ARCH_ABI)

# ========== libavcodec ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavcodec
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavcodec.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
# tell ndk-build about the dependency
LOCAL_STATIC_LIBRARIES := libavutil libx264
include $(PREBUILT_STATIC_LIBRARY)

# ========== libavdevice ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavdevice
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavdevice.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_STATIC_LIBRARY)

# ========== libavfilter ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavfilter
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavfilter.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
# tell ndk-build about the dependency
LOCAL_STATIC_LIBRARIES := libpostproc libx264
include $(PREBUILT_STATIC_LIBRARY)

# ========== libavformat ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavformat
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavformat.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
# tell ndk-build about the dependency
LOCAL_STATIC_LIBRARIES := libavutil libavcodec
include $(PREBUILT_STATIC_LIBRARY)

# ========== libavutil ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavutil
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavutil.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_STATIC_LIBRARY)

# ========== libswresample ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libswresample
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libswresample.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_STATIC_LIBRARY)

# ========== libswscale ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libswscale
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libswscale.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_STATIC_LIBRARY)

# ========== libpostproc ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libpostproc
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libpostproc.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_STATIC_LIBRARY)

# ========== libx264 ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libx264
LOCAL_SRC_FILES:= $(PATH_X264)/lib/libx264.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_STATIC_LIBRARY)

# ========== jnffmpeg (.so library) ==================
include $(CLEAR_VARS)
LOCAL_MODULE := jnffmpeg
LOCAL_LDLIBS += -llog -lz
# for x-86 shared library built warning
LOCAL_LDLIBS += -Wl,--no-warn-shared-textrel
# Ensure each static library interdepencies are defined in its respective PREBUILT_STATIC_LIBRARY block
# or setup the dependency in PREBUILT_STATIC_LIBRARY blocks
LOCAL_STATIC_LIBRARIES := libavcodec libavdevice libavfilter libavutil libavformat libswresample libswscale libx264
LOCAL_C_INCLUDES := $($(LOCAL_LIB_PATH))/include $(PATH_X264)/include
LOCAL_SRC_FILES := ./org_atalk_impl_neomedia_codec_FFmpeg.c
LOCAL_CFLAGS := -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -Wdeprecated-declarations

include $(BUILD_SHARED_LIBRARY)

# $(call import-module,ffmpeg/android/$(CPU)) // path to NDK module relative to NDK/sources/










