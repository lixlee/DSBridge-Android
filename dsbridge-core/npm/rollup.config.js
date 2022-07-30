import { terser } from 'rollup-plugin-terser';

export default [
    {
        input: 'index.js',
        output: [
            {
                file: 'build/dist/dsbridge-cjs.js',
                exports:'default',
                format: 'cjs'
            },
            {
                file: 'build/dist/dsbridge.js',
                name: 'dsBridge',
                format: 'iife',
                plugins: [terser()]
            }
        ]
    },
    {
        input: 'cocos2dx-dsbridge.js',
        output: [
            {
                dir: 'build/dist/',
                exports:'default',
                format: 'cjs'
            },
            {
                file: 'build/dist/cocos2dx-dsbridge.min.js',
                name: 'dsBridge',
                format: 'iife',
                plugins: [terser()]
            }
        ]
    }
];