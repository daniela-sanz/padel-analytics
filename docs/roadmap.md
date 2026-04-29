# Roadmap por fases

## Fase 0 - Definicion

- cerrar stack
- cerrar protocolo BLE v1
- definir entidades de datos
- acordar alcance MVP

## Fase 1 - Scaffold del proyecto Android

- crear proyecto base
- configurar Compose
- configurar navegacion
- configurar Room
- configurar DataStore
- crear estructura por paquetes

## Fase 2 - Dominio y persistencia

- modelar `Session`
- modelar `ImuSample`
- modelar `BleBlock`
- modelar `SessionEvent`
- crear DAOs y repositorios

## Fase 3 - BLE vertical slice

- escaneo y conexion
- lectura de bateria
- suscripcion a notificaciones
- parser de chunks y bloques
- almacenamiento real en BD

## Fase 4 - Flujo de sesion

- pantalla de conexion
- start / stop
- estado de grabacion
- manejo basico de reconexion

## Fase 5 - Dashboard post-sesion

- lista de sesiones
- detalle de sesion
- KPIs base
- graficas simples

## Fase 6 - Exportacion y endurecimiento

- exportacion CSV y JSON
- pruebas manuales con datos reales
- medicion de rendimiento
- ajustes de UX y robustez

## Orden de implementacion recomendado

Primero hacer que una sesion falsa pueda:

1. iniciarse
2. recibir datos simulados
3. guardarse
4. mostrarse

Despues sustituimos la fuente simulada por BLE real.

Ese enfoque reduce mucho el riesgo.
