/**
 * dsbridge for CocosCreator (JSB & WebMobile)
 */

(function () {
    function _cc_jsb_dsbridge_call(method, args) {
        var cc = window.cc;
        if (cc && cc.sys && cc.sys.isNative) {
            var jsb = window.jsb;
            if(!jsb && cc.native && cc.native.reflection) {
                jsb = cc.native;
            }
            var ret = '{"code":-2}';
            if (cc.sys.os == cc.sys.OS_IOS) {
                ret = jsb.reflection.callStaticMethod('JSBDSBridge', 'call:args:', method, args);
            } else if (cc.sys.os == cc.sys.OS_ANDROID) {
                ret = jsb.reflection.callStaticMethod("org/cocos2dx/javascript/jsb/JSBDSBridge", "call", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", method, args);
            }
            return ret;
        }
    }

    if (window.cc && window.cc.sys && window.cc.sys.isNative) {
        if (!window['_dsbridge']) {
            window['_dsbridge'] = {
                _jsb: true,
                call: _cc_jsb_dsbridge_call
            };
        }
    }
})();

import dsBridge from "./index.js"
export default dsBridge;
