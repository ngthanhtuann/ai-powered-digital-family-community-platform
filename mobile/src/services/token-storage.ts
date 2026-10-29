// Lưu access token và refresh token bằng expo-secure-store (Keychain/Keystore), theo mobile.md mục 4.3.
// Không dùng AsyncStorage, không ghi token ra log.
import * as SecureStore from 'expo-secure-store';

const ACCESS_KEY = 'fc.accessToken';
const REFRESH_KEY = 'fc.refreshToken';

// Giữ bản sao trong bộ nhớ để mỗi request không phải đọc SecureStore.
let accessCache: string | null = null;

export const tokenStorage = {
  async load(): Promise<string | null> {
    accessCache = await SecureStore.getItemAsync(ACCESS_KEY);
    return accessCache;
  },
  getAccess: () => accessCache,
  getRefresh: () => SecureStore.getItemAsync(REFRESH_KEY),
  async set(access: string, refresh: string) {
    accessCache = access;
    await SecureStore.setItemAsync(ACCESS_KEY, access);
    await SecureStore.setItemAsync(REFRESH_KEY, refresh);
  },
  async clear() {
    accessCache = null;
    await SecureStore.deleteItemAsync(ACCESS_KEY);
    await SecureStore.deleteItemAsync(REFRESH_KEY);
  },
};
