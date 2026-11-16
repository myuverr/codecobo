# Codecobo

[English](../../README.md) | [Español](README_ES.md) | [日本語](README_JA.md) | **Português** | [简体中文](README_ZH_HANS.md) | [繁體中文](README_ZH_HANT.md)

> Ajuste o áudio Bluetooth no Android, salve configurações por dispositivo e restaure-as automaticamente ao reconectar.

[![Android CI](https://github.com/myuverr/codecobo/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/myuverr/codecobo/actions/workflows/ci.yml) [![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](#como-usar) [![MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-blue)](../../LICENSE)

O Codecobo é uma ferramenta de código aberto para gerenciar codecs Bluetooth de fones de ouvido e caixas de som A2DP. A associação de dispositivos pode ser feita pela interface do sistema Android ou de forma silenciosa pelo serviço privilegiado do Shizuku.

<p align="center">
  <img src="../screenshots/home.webp" width="280" alt="Tela inicial do Codecobo">
  <img src="../screenshots/library.webp" width="280" alt="Biblioteca do Codecobo">
</p>

## Recursos

- Veja o codec, a taxa de amostragem, a profundidade de bits, o modo de canal e a qualidade LDAC em uso.
- Ajuste os parâmetros de áudio e confira o resultado no dispositivo.
- Salve configurações para cada dispositivo e restaure-as ao reconectar.
- Ative ou desative a restauração por dispositivo ou pause todas de uma vez.
- Acompanhe o estado do áudio por uma notificação permanente.

## Download

[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80" title="Badge by flocke · CC BY-SA 3.0">](https://github.com/myuverr/codecobo/releases/latest)

## Como usar

Requer Android 13 ou superior. Não precisa de root.

1. Conecte os fones de ouvido ou a caixa de som nas configurações de Bluetooth do Android.
2. Abra o Codecobo, conceda a permissão de "Dispositivos por perto" e selecione o dispositivo que deseja ajustar.
3. Escolha o codec e os parâmetros de áudio e toque em **Salvar**. No primeiro uso, conclua a associação do dispositivo se for solicitado.
4. Ative a restauração automática se quiser recuperar a configuração ao reconectar o dispositivo. Gerencie as configurações salvas na **Biblioteca**.

A notificação permanente de estado pode ser ativada separadamente em **Configurações**.

## Compilar a partir do código-fonte

Para desenvolvimento e depuração, compile a versão Debug:

```sh
./gradlew assembleDebug
```

Para gerar o APK de Release:

```sh
./gradlew assembleRelease
```

## Licença

Licenciado sob a [MPL-2.0](../../LICENSE).
