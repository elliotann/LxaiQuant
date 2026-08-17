"use strict";
var __webpack_require__ = {};
(()=>{
    __webpack_require__.d = function(exports1, definition) {
        for(var key in definition)if (__webpack_require__.o(definition, key) && !__webpack_require__.o(exports1, key)) Object.defineProperty(exports1, key, {
            enumerable: true,
            get: definition[key]
        });
    };
})();
(()=>{
    __webpack_require__.o = function(obj, prop) {
        return Object.prototype.hasOwnProperty.call(obj, prop);
    };
})();
(()=>{
    __webpack_require__.r = function(exports1) {
        if ('undefined' != typeof Symbol && Symbol.toStringTag) Object.defineProperty(exports1, Symbol.toStringTag, {
            value: 'Module'
        });
        Object.defineProperty(exports1, '__esModule', {
            value: true
        });
    };
})();
var __webpack_exports__ = {};
__webpack_require__.r(__webpack_exports__);
__webpack_require__.d(__webpack_exports__, {
    VantImports: ()=>VantImports,
    VantResolver: ()=>VantResolver
});
function kebabCase(key) {
    const result = key.replace(/([A-Z])/g, ' $1').trim();
    return result.split(' ').join('-').toLowerCase();
}
function getModuleType(options) {
    const { ssr, module = 'esm' } = options;
    if (void 0 !== ssr) return ssr ? 'lib' : 'es';
    return 'cjs' === module ? 'lib' : 'es';
}
function getSideEffects(dirName, options) {
    const { importStyle = true } = options;
    if (!importStyle) return;
    const moduleType = getModuleType(options);
    if ('less' === importStyle) return `vant/${moduleType}/${dirName}/style/less`;
    return `vant/${moduleType}/${dirName}/style/index`;
}
function getAPIMap() {
    const apiMap = new Map();
    const api = {
        dialog: [
            'showDialog',
            'closeDialog',
            'showConfirmDialog',
            'setDialogDefaultOptions',
            'resetDialogDefaultOptions'
        ],
        imagePreview: [
            'showImagePreview'
        ],
        notify: [
            'showNotify',
            'closeNotify',
            'setNotifyDefaultOptions',
            'resetNotifyDefaultOptions'
        ],
        toast: [
            'showToast',
            'closeToast',
            'showFailToast',
            'showLoadingToast',
            'showSuccessToast',
            'allowMultipleToast',
            'setToastDefaultOptions',
            'resetToastDefaultOptions'
        ]
    };
    Object.entries(api).forEach(([importName, apiList])=>{
        apiList.forEach((api)=>{
            apiMap.set(api, importName);
        });
    });
    return apiMap;
}
function VantResolver(options = {}) {
    const moduleType = getModuleType(options);
    const apiMap = getAPIMap();
    return {
        type: 'component',
        resolve: (name)=>{
            var _options_exclude;
            if (name.startsWith('Van')) {
                var _options_exclude1;
                const partialName = name.slice(3);
                if (!(null === (_options_exclude1 = options.exclude) || void 0 === _options_exclude1 ? void 0 : _options_exclude1.includes(partialName))) return {
                    name: partialName,
                    from: `vant/${moduleType}`,
                    sideEffects: getSideEffects(kebabCase(partialName), options)
                };
            }
            if (apiMap.has(name) && !(null === (_options_exclude = options.exclude) || void 0 === _options_exclude ? void 0 : _options_exclude.includes(name))) {
                const partialName = apiMap.get(name);
                return {
                    name,
                    from: `vant/${moduleType}`,
                    sideEffects: getSideEffects(kebabCase(partialName), options)
                };
            }
        }
    };
}
function VantImports(options = {}) {
    const moduleType = getModuleType(options);
    return {
        [`vant/${moduleType}`]: [
            ...getAPIMap().keys()
        ]
    };
}
var __webpack_export_target__ = exports;
for(var __webpack_i__ in __webpack_exports__)__webpack_export_target__[__webpack_i__] = __webpack_exports__[__webpack_i__];
if (__webpack_exports__.__esModule) Object.defineProperty(__webpack_export_target__, '__esModule', {
    value: true
});
