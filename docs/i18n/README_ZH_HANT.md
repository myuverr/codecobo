# Codecobo

[English](../../README.md) | [Español](README_ES.md) | [日本語](README_JA.md) | [Português](README_PT.md) | [简体中文](README_ZH_HANS.md) | **繁體中文**

> 在 Android 上調整藍牙音訊參數，儲存裝置設定並在重新連線時自動還原。

[![Android CI](https://github.com/myuverr/codecobo/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/myuverr/codecobo/actions/workflows/ci.yml) [![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](#使用) [![MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-blue)](../../LICENSE)

Codecobo 是一款開源的藍牙編解碼器管理工具，適用於 A2DP 耳機和喇叭。支援透過 Android 系統介面關聯裝置，也可使用 Shizuku 特權服務靜默完成關聯。

<p align="center">
  <img src="../screenshots/home.webp" width="280" alt="Codecobo 首頁">
  <img src="../screenshots/library.webp" width="280" alt="Codecobo 設定庫">
</p>

## 功能

- 查看目前的編解碼器、取樣率、位元深度、聲道及 LDAC 音質設定。
- 調整音訊參數，並查看套用後的實際狀態。
- 為不同裝置儲存設定，重新連線時自動還原。
- 分別開啟或關閉各裝置的自動還原，或一次暫停全部還原。
- 透過常駐通知查看目前的音訊狀態。

## 下載

[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80" title="Badge by flocke · CC BY-SA 3.0">](https://github.com/myuverr/codecobo/releases/latest)

## 使用

支援 Android 13 以上版本，無需 Root。

1. 在系統藍牙設定中連接耳機或喇叭。
2. 開啟 Codecobo，允許「附近裝置」權限，選擇要調整的裝置。
3. 選擇編解碼器和音訊參數，點選**儲存**。首次使用時，依照提示完成裝置關聯。
4. 需要在重新連線時還原設定，可為該裝置開啟自動還原。已儲存的設定可在**設定庫**中管理。

常駐狀態通知可在**設定**中單獨開啟。

## 從原始碼建置

開發與偵錯時建置 Debug 版本：

```sh
./gradlew assembleDebug
```

建置 Release APK：

```sh
./gradlew assembleRelease
```

## 授權

本專案採用 [MPL-2.0](../../LICENSE) 授權條款。
