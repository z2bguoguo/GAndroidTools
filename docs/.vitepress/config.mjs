// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
import { defineConfig } from 'vitepress';
const sidebar = (prefix, en) => [{ text: en ? 'Guide' : '使用指南', items: [
 { text: en ? 'Get started' : '快速开始', link: `${prefix}/guide/start` },
 { text: en ? 'Tools and settings' : '工具与设置', link: `${prefix}/guide/tools` },
 { text: en ? 'Macros API' : '宏脚本 API', link: `${prefix}/guide/macros` },
 { text: en ? 'Page archive format' : '页面快照格式', link: `${prefix}/guide/page-format` },
 { text: en ? 'Compatibility' : '兼容性与验证', link: `${prefix}/guide/compatibility` },
 { text: en ? 'Build and release' : '开发与发布', link: `${prefix}/development/build` }
]}];
export default defineConfig({
 title: 'GAndroidTools', description: 'A Shizuku-powered Android toolbox', base: '/GAndroidTools/',
 cleanUrls: true, lastUpdated: true,
 locales: { root: { label: '简体中文', lang: 'zh-CN', themeConfig: { sidebar: sidebar('', false), nav: [{text:'指南',link:'/guide/start'}] } }, en: { label: 'English', lang: 'en', themeConfig: { sidebar: sidebar('/en', true), nav: [{text:'Guide',link:'/en/guide/start'}] } } },
 themeConfig: { socialLinks: [{ icon: 'github', link: 'https://github.com/z2bguoguo/GAndroidTools' }], search: { provider: 'local' }, footer: { message: 'Released under the <a href="https://github.com/z2bguoguo/GAndroidTools/blob/main/LICENSE">GPL-3.0-or-later License</a>.', copyright: 'GAndroidTools contributors' } }
});
