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
export { VantImports, VantResolver };
