# מכניס את מודל זיהוי הדיבור של עזרים לתמונת /system.
# שימוש: מניחים את התיקייה hebrew-asr (7 הקבצים) ליד הקובץ הזה, ומוסיפים ל-device.mk:
#   $(call inherit-product, <נתיב>/ezrim-asr.mk)
# הנתיב בתוך המכשיר חייב להתאים ל-ModelLocator.DIR באפליקציה.
EZRIM_ASR_SRC := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))/hebrew-asr
PRODUCT_COPY_FILES += $(foreach f,$(wildcard $(EZRIM_ASR_SRC)/*),$(f):$(TARGET_COPY_OUT_SYSTEM)/etc/ezrim/hebrew-asr/$(notdir $(f)))

# הענקה מראש של הרשאת המיקרופון וההתראות (בלי בקשה מהמשתמש)
PRODUCT_COPY_FILES += $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))/default-permissions-ezrim.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/default-permissions/default-permissions-ezrim.xml
