// Bản APK release của Android chặn HTTP không mã hóa. Plugin này chỉ cho phép HTTP khi
// EXPO_PUBLIC_API_BASE_URL lúc build là http:// (bản demo trỏ máy chạy backend trong mạng LAN, SCRUM-93).
// Build trỏ https:// thì giữ mặc định của Android. Dùng expo/config-plugins có sẵn, không thêm package.
const { AndroidConfig, withAndroidManifest } = require('expo/config-plugins');

module.exports = function withCleartextTraffic(config) {
  const apiUrl = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';
  if (!apiUrl.startsWith('http://')) return config;
  return withAndroidManifest(config, (mod) => {
    AndroidConfig.Manifest.getMainApplicationOrThrow(mod.modResults).$[
      'android:usesCleartextTraffic'
    ] = 'true';
    return mod;
  });
};
