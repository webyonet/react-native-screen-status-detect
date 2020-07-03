import { NativeModules, DeviceEventEmitter, Platform } from 'react-native';

const { ScreenStatusDetect: NativeScreenStatusDetect } = NativeModules;

let LISTENERS = {};
let ID = 0;
let META = '__listener_id';

/**
 * @return {string}
 */
function GetKey(listener) {
    if (!listener.hasOwnProperty(META)) {
        if (!Object.isExtensible(listener)) {
            return 'F';
        }

        Object.defineProperty(listener, META, {
            value: `L${++ID}`
        });
    }

    return listener[META];
}

export default class ScreenStatusDetect {

    static async getCurrentStatus() {
        return await NativeScreenStatusDetect.getCurrentStatus();
    }

    static addListener(callback) {
        if (typeof callback === 'function') {
            let key = GetKey(callback);

            LISTENERS[key] = DeviceEventEmitter.addListener('screenStatusChange', callback);
        } else {
            console.error('callback is not a function');
        }
    }

    static removeListener(callback) {
        if (typeof callback === 'function') {
            let key = GetKey(callback);

            if (LISTENERS[key]) {
                LISTENERS[key].remove();
                LISTENERS[key] = null;
            }
        } else {
            console.error('callback is not a function');
        }
    }

    static enableSecureScreen() {
        if (Platform.OS === 'android') {
            NativeScreenStatusDetect.enableSecureScreen();
        } else {
            console.log('(enableSecureScreen) this method is only available on android platform');
        }
    }

    static disableSecureScreen() {
        if (Platform.OS === 'android') {
            NativeScreenStatusDetect.disableSecureScreen();
        } else {
            console.log('(disableSecureScreen) this method is only available on android platform');
        }
    }
}
