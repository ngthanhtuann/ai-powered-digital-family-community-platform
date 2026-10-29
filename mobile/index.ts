// react-native-gesture-handler phải được import trước tiên (yêu cầu của menu drawer).
import 'react-native-gesture-handler';
import { registerRootComponent } from 'expo';

import App from './App';

registerRootComponent(App);
