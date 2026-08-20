# Release checklist

- [x] Application ID and version are configurable in source
- [x] Debug API URL is isolated from release cleartext policy
- [x] Release minification/resource shrinking configured
- [x] CI builds debug APK and unsigned release AAB
- [ ] Supply production HTTPS API URL
- [ ] Configure Google OAuth client IDs and consent screen
- [ ] Configure Play product IDs, service account and purchase verification
- [ ] Create Play App Signing key through the approved release process
- [ ] Generate launcher/adaptive icon and Play listing artwork
- [ ] Complete PDF/DOCX worker and export renderer
- [ ] Run device matrix and accessibility tests
- [ ] Complete privacy/terms legal review and Data Safety form
- [ ] Run dependency/license/security audit against locked release versions
- [ ] Perform external penetration test
- [ ] Upload internal-testing AAB and test purchases, restore, grace and cancellation

Artifact locations after build:

- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Release AAB: `android/app/build/outputs/bundle/release/app-release.aab`

