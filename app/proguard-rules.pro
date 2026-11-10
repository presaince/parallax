# GlassTube release rules. Minification is off for now; this file exists so enabling it later
# does not also require guessing which reflection the extractor needs.
#
# NewPipeExtractor parses JSON into models with nanojson, not reflection, so it should survive R8.
# These keeps are the cheap insurance for the day minification is switched on.

-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.github.TeamNewPipe.nanojson.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
