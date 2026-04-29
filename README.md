# MVP App - Wearable de Padel

Este directorio contiene la base de la aplicacion movil del MVP para el sistema wearable del TFG.

## Decision principal

Para este MVP, la mejor opcion es:

- `Android nativo`
- `Kotlin`
- `Jetpack Compose`
- `BLE nativo de Android`
- `Room` para almacenamiento local
- `DataStore` para preferencias y metadatos simples

## Por que esta opcion

- El PDF ya acota la primera version a `Android`.
- BLE es una parte critica y sensible; en Android nativo tenemos mas control y menos friccion.
- Compose acelera la construccion de UI sin sacrificar arquitectura.
- Room encaja muy bien con sesiones, bloques, muestras y exportacion.
- Nos permite separar con claridad `captura`, `persistencia`, `procesado` y `dashboard`.

## Alcance del MVP

La app debe cubrir:

- conexion BLE con el wearable
- inicio y fin de sesion
- recepcion de bloques de datos IMU
- almacenamiento local por sesion
- exportacion de sesion
- dashboard post-sesion con KPIs y graficas
- metadatos basicos de usuario
- bateria del dispositivo

## Estructura propuesta

```text
app/
  README.md
  docs/
    architecture.md
    ble-protocol-v1.md
    ble-transport-v1-concrete.md
    mvp-scope.md
    roadmap.md
    adr/
      0001-android-native-kotlin-compose.md
  mobile-android/
    README.md
```

## Principios de ingenieria

- Primero cerramos alcance y contratos de datos.
- Luego construimos el esqueleto del proyecto.
- Despues implementamos vertical slices pequenas y verificables.
- El firmware y la app deben acordar un protocolo estable antes de optimizar.

## Siguiente paso recomendado

Crear el scaffold real del proyecto Android en `app/mobile-android` con:

- arquitectura por capas
- pantallas base
- dominio de sesiones
- capa BLE simulada para desarrollo temprano
