# Codecobo

[English](../../README.md) | **Español** | [日本語](README_JA.md) | [Português](README_PT.md) | [简体中文](README_ZH_HANS.md) | [繁體中文](README_ZH_HANT.md)

> Ajusta el audio Bluetooth en Android, guarda una configuración para cada dispositivo y restáurala al volver a conectarlo.

[![Android CI](https://github.com/myuverr/codecobo/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/myuverr/codecobo/actions/workflows/android.yml) [![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](#uso) [![MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-blue)](../../LICENSE)

Codecobo es una herramienta de código abierto para gestionar los códecs Bluetooth de auriculares y altavoces A2DP. Los dispositivos pueden asociarse desde la interfaz del sistema de Android o de forma silenciosa mediante el servicio privilegiado de Shizuku.

<p align="center">
  <img src="../screenshots/home.webp" width="280" alt="Inicio de Codecobo">
  <img src="../screenshots/library.webp" width="280" alt="Biblioteca de Codecobo">
</p>

## Funciones

- Consulta el códec, la frecuencia de muestreo, la profundidad de bits, el modo de canal y la calidad LDAC actuales.
- Ajusta los parámetros de audio y consulta el resultado en el dispositivo.
- Guarda la configuración de cada dispositivo y restáurala cuando vuelva a conectarse.
- Activa o desactiva la restauración por dispositivo, o páusala para todos.
- Consulta el estado del audio mediante una notificación permanente.

## Descarga

[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80" title="Badge by flocke · CC BY-SA 3.0">](https://github.com/myuverr/codecobo/releases/latest)

## Uso

Requiere Android 13 o posterior. No necesita acceso root.

1. Conecta los auriculares o el altavoz desde los ajustes de Bluetooth de Android.
2. Abre Codecobo, concede el permiso de "Dispositivos cercanos" y selecciona el dispositivo que quieras ajustar.
3. Elige el códec y los parámetros de audio y pulsa **Guardar**. La primera vez, completa la asociación del dispositivo si se solicita.
4. Activa la restauración automática si quieres recuperar la configuración al reconectar el dispositivo. Gestiona las configuraciones guardadas en **Biblioteca**.

La notificación permanente de estado se puede activar por separado en **Ajustes**.

## Compilar desde el código fuente

Para desarrollo y depuración, compila la versión Debug:

```sh
./gradlew assembleDebug
```

Para generar el APK de Release:

```sh
./gradlew assembleRelease
```

## Licencia

Distribuido bajo la licencia [MPL-2.0](../../LICENSE).
