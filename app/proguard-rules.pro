# App-specific R8 keep rules. Library rules (Room, Media3, Chaquopy, Compose)
# ship as consumer rules inside their artifacts and apply automatically.
# Keep this file minimal: prefer fixing reflection at the source over keeps.

# Keep crash reports readable.
#
# Your Pixel's R8 download crash came back with frames like `j0.z0.f` and
# `hf.d.b(r8-map-id-0864bb…)` — the exception was diagnosable only because it originated in
# Python, whose frames R8 never touched. A crash in our own Kotlin would have been a wall of
# one-letter names.
#
# These keep the source file and line numbers so a trace can be retraced against the build's
# mapping.txt (R8 already stamps the map id into the trace for exactly that purpose). The
# rename keeps the file name itself obfuscated, which is the standard trade.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
