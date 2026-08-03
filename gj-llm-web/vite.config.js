import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';
import { resolve } from 'path';
import { readdirSync, existsSync } from 'fs';
// 预构建 element-plus 按需导入用到的组件，解决 dev 下两个性能问题：
// 1) 按需导入的深层路径（element-plus/es/components/xxx）不在源码静态 import 里，
//    vite 启动扫描发现不了，运行时才“发现新依赖”-> 触发重新预构建 + 强制全页 reload；
// 2) 每个组件内部有数十个 ESM 子模块，dev 下浏览器逐个请求（HTTP/1.1 并发受限），
//    刷新时几百个小请求排队。预构建把每个组件合并成单文件，请求数大幅下降。
//
// 注意：element-plus 的 package.json exports 把 `./es/*` 映射到 `./es/*.mjs`（单文件），
// 所以目录形式的 `element-plus/es/components/xxx` 解析失败；JS 入口必须用完整路径
// `.../index.mjs`（走 `./es/*.mjs` 规则），样式入口用 `.../style/css`（映射到 style/css.mjs）。
const epComponentsRoot = resolve(__dirname, 'node_modules/element-plus/es/components');
const epOptimizeDeps = [
    'element-plus/es',
    ...readdirSync(epComponentsRoot, { withFileTypes: true })
        .filter((d) => d.isDirectory() && !d.name.startsWith('.'))
        .flatMap((d) => {
        const entries = [];
        const base = `element-plus/es/components/${d.name}`;
        if (existsSync(resolve(epComponentsRoot, d.name, 'index.mjs'))) {
            entries.push(`${base}/index.mjs`);
        }
        if (existsSync(resolve(epComponentsRoot, d.name, 'style', 'css.mjs'))) {
            entries.push(`${base}/style/css`);
        }
        return entries;
    }),
];
export default defineConfig({
    plugins: [
        vue(),
        AutoImport({
            resolvers: [ElementPlusResolver()],
            dts: 'src/types/auto-imports.d.ts',
        }),
        Components({
            resolvers: [ElementPlusResolver()],
            dts: 'src/types/components.d.ts',
        }),
    ],
    resolve: {
        alias: {
            '@': resolve(__dirname, 'src'),
        },
    },
    optimizeDeps: {
        include: epOptimizeDeps,
    },
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
});
