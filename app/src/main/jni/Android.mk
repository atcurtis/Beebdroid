LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -DANDROID_NDK \
                -DDISABLE_IMPORTGL
                
LOCAL_MODULE    := bbcmicro
LOCAL_LDLIBS += -Wl,--no-warn-shared-textrel

ifeq ($(TARGET_ARCH),arm)
    LOCAL_CFLAGS +=  -march=armv7 -O3 -D_ARM_
    #LOCAL_CFLAGS +=  -march=armv6t2 -O3 -D_ARM_
    LOCAL_SRC_FILES := 6502asm_arm.S
endif
ifeq ($(TARGET_ARCH),x86)
    LOCAL_CFLAGS += -m32
    LOCAL_SRC_FILES := 6502asm_x86.S
endif

LOCAL_SRC_FILES += \
    6502.c \
    8271.c \
    adc.c \
    disc.c \
    main.c \
    sound.c \
    ssd.c \
    sysvia.c \
    uservia.c \
    video.c


LOCAL_LDLIBS += -lm -llog -ljnigraphics -lz -lGLESv2

include $(BUILD_SHARED_LIBRARY)
