# Codecobo

[English](../../README.md) | [Español](README_ES.md) | [日本語](README_JA.md) | [Português](README_PT.md) | **简体中文** | [繁體中文](README_ZH_HANT.md)

> 在 Android 上调整蓝牙音频参数，保存设备配置并在重连时自动恢复。

[![Android CI](https://github.com/myuverr/codecobo/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/myuverr/codecobo/actions/workflows/android.yml) [![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](#使用) [![MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-blue)](../../LICENSE)

Codecobo 是一款开源的蓝牙编解码器管理工具，适用于 A2DP 耳机和音箱。支持通过 Android 系统界面关联设备，也支持使用 Shizuku 特权静默关联。

<p align="center">
  <img src="../screenshots/home.webp" width="280" alt="Codecobo 首页">
  <img src="../screenshots/library.webp" width="280" alt="Codecobo 配置库">
</p>

## 功能

- 查看当前编解码器、采样率、位深、声道和 LDAC 音质设置。
- 调整音频参数，并查看修改后的实际状态。
- 为不同设备保存配置，重连时自动恢复。
- 单独开关每台设备的自动恢复，或一键暂停全部恢复。
- 通过常驻通知查看当前音频状态。

## 下载

[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80" title="Badge by flocke · CC BY-SA 3.0">](https://github.com/myuverr/codecobo/releases/latest)

## 使用

支持 Android 13 及以上，无需 Root。

1. 在系统蓝牙设置中连接耳机或音箱。
2. 打开 Codecobo，允许“附近设备”权限，选择要调整的设备。
3. 选择编解码器和音频参数，点击**保存**。首次使用时，按提示完成设备关联。
4. 需要重连恢复时，为该设备开启自动恢复。已保存的配置在**配置库**中管理。

常驻状态通知可在**设置**中单独开启。

## 从源码构建

开发和调试时构建 Debug 版本：

```sh
./gradlew assembleDebug
```

构建 Release APK：

```sh
./gradlew assembleRelease
```

## 许可证

本项目采用 [MPL-2.0](../../LICENSE) 许可证。
