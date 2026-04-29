# Proyecto Android

Aqui ira el scaffold real de la app Android.

## Estructura objetivo

```text
mobile-android/
  app/
    src/main/java/com/tfg/wearableapp/
      core/
        ble/
      data/
        ble/
      feature/
        session/
  build.gradle.kts
  settings.gradle.kts
```

## Paquetes recomendados

```text
com.tfg.wearableapp
com.tfg.wearableapp.core
com.tfg.wearableapp.data
com.tfg.wearableapp.domain
com.tfg.wearableapp.feature.connection
com.tfg.wearableapp.feature.session
com.tfg.wearableapp.feature.dashboard
com.tfg.wearableapp.feature.settings
```

## Proximo paso

Generar el scaffold Android real y dejar montado el primer flujo:

- pantalla de conexion
- sesion simulada
- almacenamiento local basico

## Base ya creada

Ya existe una primera base de transporte BLE en Kotlin:

- modelos de `chunk`, `bloque` y `muestra`
- parser de chunk binario
- parser de bloque logico
- reensamblador de chunks
- pipeline `chunk -> bloque`
- serializador de bloques para pruebas
- fuente BLE simulada
- esqueleto para `BluetoothGattCallback`

## Nota sobre MTU

La informacion real del MTU en Android se obtiene tras negociar la conexion BLE:

- se solicita con `requestMtu(...)`
- se confirma en `onMtuChanged(gatt, mtu, status)`

Notas ampliadas en:

- `../docs/ble-mtu-notes.md`

## Estado actual del scaffold

Queda preparado un primer proyecto Android con:

- `Compose`
- `MainActivity`
- pantalla de demo de transporte
- simulacion end-to-end de notificaciones BLE
- punto de entrada para conexion BLE real
