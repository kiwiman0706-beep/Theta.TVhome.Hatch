# ADB の窓口・ハブの部品は Manifest から名前で呼ばれる（R8 は Manifest を見るので通常は不要。念のため）
-keep class app.thetahatch.adb.** { *; }
