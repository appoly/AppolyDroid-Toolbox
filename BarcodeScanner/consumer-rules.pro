# No consumer R8/ProGuard rules required for this module.
#
# It ships no @Serializable models, Room entities/converters, reflection, or JNI — ScannedBarcode
# and BarcodeFormat are plain Kotlin, and nothing looks them up by name. The ML Kit and Play
# services artifacts carry their own consumer rules for the classes R8 would otherwise strip from
# their reflective/native call paths, so there is nothing to restate here.
#
# This file is intentionally rule-free (kept so the absence of keeps is a deliberate, reviewed
# decision rather than an oversight).
