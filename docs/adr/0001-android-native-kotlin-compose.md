# ADR 0001 - Android nativo con Kotlin y Jetpack Compose

## Estado

Aprobado para el MVP

## Contexto

El MVP requiere:

- Android como plataforma inicial
- integracion BLE robusta
- almacenamiento local
- iteracion rapida sobre UI y flujo de sesiones

BLE es el nucleo tecnico del producto y la estabilidad es mas importante que la portabilidad temprana.

## Decision

La aplicacion se desarrollara con:

- `Kotlin`
- `Jetpack Compose`
- `Android BLE APIs`
- `Room`
- `DataStore`

## Consecuencias positivas

- mejor control de BLE
- menor capa de abstraccion
- base muy solida para pruebas de campo
- arquitectura clara para un producto orientado a adquisicion de datos

## Consecuencias negativas

- no es multiplataforma
- requiere mas trabajo si en el futuro se quiere iOS

## Alternativas descartadas

### Flutter

Buena opcion para producto generalista, pero menos atractiva aqui por:

- dependencia mayor de plugins BLE
- depuracion mas incomoda en casos de reconexion y MTU

### React Native

Descartado por motivos similares y por encajar peor en una app tecnica con adquisicion BLE sostenida.
