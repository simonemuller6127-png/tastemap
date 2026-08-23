@echo off
rem Print debug keystore fingerprints (SHA1/SHA256) - for Amap console registration.
rem Debug keystore: %USERPROFILE%\.android\debug.keystore (auto-generated on first build).
rem Backup copy: D:\tastemap-keys\debug.keystore.backup - restore it to keep SHA1 stable.
rem If keytool is not found: open a NEW cmd window (PATH updates only in new windows),
rem or run "call env.bat" first. Keep this file ASCII-only (GBK codepage reads .bat).
"D:\jdk-17\bin\keytool.exe" -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android | findstr /i "SHA1 SHA256"
