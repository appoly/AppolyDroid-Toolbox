# No consumer R8/ProGuard rules required for this module.
#
# It ships no @Serializable models, Room entities/converters, reflection, or JNI. The CameraX and
# ML Kit artifacts carry their own consumer rules covering the classes their native and reflective
# call paths need kept, so there is nothing to restate here.
#
# This file is intentionally rule-free (kept so the absence of keeps is a deliberate, reviewed
# decision rather than an oversight).
