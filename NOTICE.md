# Third-party notices

MilkDrop TV includes projectM.
projectM is licensed under the GNU Lesser General Public License version 2.1.
The license text is `projectM-LGPL-2.1.txt` in the APK assets and in each GitHub release.

The APK also includes these MIT components inside `libprojectM-4.so`:

- projectm-eval
- hlslparser

## Source

`projectM-source.txt` names the exact projectM commit in the build.
The same file is in the APK at `assets/projectM-source.txt`.

## Replace the library

1. Download the projectM source named in `projectM-source.txt`.
2. Build a compatible `libprojectM-4.so` for `arm64-v8a` or `armeabi-v7a`.
3. Unzip the APK.
4. Replace `lib/<abi>/libprojectM-4.so` with the new library.
5. Sign the APK again.
6. Install the signed APK.
