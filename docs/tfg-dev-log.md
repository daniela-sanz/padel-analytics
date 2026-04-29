# Informe de desarrollo del MVP de la app

## Proposito del documento

Este documento recoge de forma secuencial las decisiones, cambios, problemas encontrados y avances realizados durante la definicion e implementacion inicial del MVP de la aplicacion movil del TFG.

Su objetivo es servir como:

- bitacora tecnica del desarrollo
- apoyo para la redaccion posterior de la memoria
- registro de decisiones de arquitectura
- trazabilidad entre requisitos, implementacion y validacion

## Alcance de este informe

Este informe resume el trabajo realizado hasta la puesta en marcha de una primera app Android funcional con simulacion local del flujo BLE.

Incluye:

- analisis inicial de requisitos
- seleccion del stack tecnologico
- definicion del protocolo de transporte BLE
- construccion del scaffold Android
- creacion de una simulacion funcional del flujo de datos
- incidencias de entorno y su resolucion

## 1. Contexto inicial

El punto de partida fue el documento:

- `DISPOSITIVO CENTRADO EN ADQUISICION Y ENVIO.pdf`

junto con:

- las `hojas_de_caracteristicas`
- codigo previo del proyecto
- dashboards conceptuales ya existentes
- scripts de validacion de hardware
- resultados del estudio de frecuencia

Desde el principio se definio que esta primera fase del sistema estaria centrada en:

- adquisicion continua en el wearable
- envio de datos por BLE
- procesamiento y almacenamiento en el movil
- visualizacion posterior mediante dashboard

## 2. Lectura de requisitos del MVP

Del documento de requisitos se extrajeron las siguientes decisiones clave del MVP:

- el wearable se limita a adquisicion y envio
- la transmision no sera muestra a muestra, sino por bloques
- el control de sesion se hara principalmente desde la app
- el wearable debera tolerar cortes breves de BLE
- el almacenamiento temporal en el wearable se hara con `FIFO + RAM`
- la app inicial sera `Android`
- la app debe cubrir conexion, inicio y fin de sesion, recepcion, guardado local, exportacion y dashboard post-sesion

Tambien quedaron fijadas varias decisiones de señal:

- IMU continua
- acelerometro `+-16 g`
- giroscopio `+-2000 dps`
- almacenamiento de datos crudos: `ax, ay, az, gx, gy, gz`
- alineacion temporal aproximada con timestamp de bloque y contador de muestras

## 3. Reutilizacion de trabajo previo

Se reviso el material existente en el repositorio y aparecieron varios elementos utiles:

- dashboards HTML de referencia visual
- scripts de validacion del hardware wearable
- analisis del comportamiento de la IMU a distintas frecuencias
- resultados de procesamiento de datos de padel

Uno de los resultados mas importantes fue la existencia de un estudio previo que apoyaba:

- `104 Hz` como frecuencia objetivo razonable para el MVP

Esta frecuencia se tomo como referencia inicial para el diseno del transporte BLE y de la capa de datos de la app.

## 4. Decision del stack tecnologico

Tras revisar el alcance del MVP, se decidio desarrollar la aplicacion con:

- `Android nativo`
- `Kotlin`
- `Jetpack Compose`
- `BLE nativo de Android`
- `Room` para persistencia futura
- `DataStore` para preferencias futuras

### Justificacion

La decision no se tomo por moda ni por preferencia estetica, sino por adecuacion tecnica al problema.

La aplicacion depende fuertemente de:

- comunicacion BLE
- recepcion sostenida de datos
- control fino de permisos y conexion
- capacidad de depuracion cercana al sistema

Se compararon implicitamente tres familias de opciones:

### Android nativo con Kotlin

Ventajas:

- control directo de BLE
- menos dependencias intermedias
- mejor encaje con un MVP Android-only
- mejor base para depurar `MTU`, reconexiones y callbacks BLE

### React Native

Ventajas:

- mayor familiaridad para perfiles web

Inconvenientes para este caso:

- mayor dependencia de plugins BLE
- menor control sobre detalles de bajo nivel
- mas puntos de fallo para un MVP centrado en adquisicion y transporte binario

### Flutter

Ventajas:

- muy buena experiencia de UI

Inconvenientes para este caso:

- el soporte BLE sigue dependiendo de integraciones no tan directas como en Android nativo

### Conclusion

Para este proyecto se considero que `Kotlin + Android nativo` era la opcion mas simple en sentido ingenieril:

- menos riesgo
- mejor control
- mejor alineacion con el objetivo del MVP

## 5. Primera estructura documental creada

Dentro de `app` se creo una base documental para no arrancar el proyecto sin decisiones explicitas.

Se generaron los siguientes documentos:

- `README.md`
- `docs/architecture.md`
- `docs/ble-protocol-v1.md`
- `docs/ble-transport-v1-concrete.md`
- `docs/mvp-scope.md`
- `docs/roadmap.md`
- `docs/adr/0001-android-native-kotlin-compose.md`

### Objetivo de esta capa documental

- fijar alcance
- dejar trazabilidad de decisiones
- separar arquitectura de implementacion
- evitar improvisar el protocolo BLE

## 6. Discusion sobre el tamano de los bloques BLE

Durante la conversacion aparecio una cuestion importante:

- si `104 Hz` y `2 s` por bloque eran viables

Se calculo:

- `104 muestras/s * 2 s = 208 muestras`
- `6 canales * int16 = 12 bytes por muestra`
- `208 * 12 = 2496 bytes` solo de payload IMU

### Aclaracion importante

No se concluyo que esos datos no puedan transmitirse por BLE en total.

La conclusion correcta fue:

- no caben en una sola notificacion ATT
- si pueden transmitirse como una secuencia de multiples notificaciones

Esta matizacion fue importante porque aclara una confusion habitual:

- una cosa es el volumen total de datos de una sesion
- otra distinta es el tamano maximo de una unidad de envio ATT

## 7. Fuente tecnica de la limitacion de MTU

Se identificaron las bases tecnicas del problema de tamano:

- el `MTU` real en Android se obtiene con `requestMtu(...)`
- el valor final negociado se recibe en `onMtuChanged(...)`
- en notificaciones ATT, el payload util se aproxima a `ATT_MTU - 3`

Esto se documento en:

- `docs/ble-mtu-notes.md`

Tambien se dejaron referencias oficiales para justificar estas decisiones en el futuro:

- documentacion de `BluetoothGatt`
- documentacion de `BluetoothGattCallback`
- especificacion del protocolo ATT de Bluetooth

## 8. Evolucion del diseno de transporte BLE

Inicialmente se hablo de bloques de `~2 s`, en linea con la idea general del PDF.

Sin embargo, al aterrizar la implementacion del transporte, se opto por una primera configuracion mas conservadora para el MVP tecnico:

- `104 Hz`
- `0.5 s` por bloque logico
- `52 muestras por bloque`

Esto da:

- `52 * 12 = 624 bytes` de payload IMU
- `26 bytes` de cabecera de bloque
- `650 bytes` de bloque logico total

### Chunking propuesto

Se propuso una separacion entre:

1. `bloque logico`
2. `chunk BLE`

Y para el primer MVP tecnico:

- `payload util por chunk = 180 bytes`
- `4 chunks` por bloque logico de `650 bytes`
- `timeout de reensamblado = 3 s`

### Motivos del cambio a 0.5 s

- menor latencia
- menor consumo de RAM en el wearable
- menor impacto si se pierde un bloque
- mayor facilidad de depuracion
- simplificacion del reensamblado inicial

## 9. Definicion del bloque logico

Se propuso una estructura fija para el bloque logico, con campos como:

- `protocol_version`
- `block_type`
- `flags`
- `packet_id`
- `timestamp_block_start_ms`
- `sample_start_index`
- `sample_count`
- `step_count_total`
- `battery_level_pct`
- `status_flags`

Y una lista de muestras:

- `ax, ay, az, gx, gy, gz`

Este contrato se penso para que luego pudiera traducirse con facilidad a:

- `struct` en firmware
- parser binario en Kotlin

## 10. Definicion de reensamblado en la app

Se definio que la app:

- mantiene un buffer temporal por `packet_id`
- recibe chunks individuales
- los guarda por `chunk_index`
- concatena el bloque cuando esten todos presentes
- parsea el bloque logico
- persiste el resultado en pasos posteriores

Tambien se definieron reglas de deteccion de fallos:

- salto en `packet_id`
- ausencia de chunks dentro de un bloque
- discontinuidad en `sample_start_index`

## 11. Primera implementacion Kotlin de la capa de transporte

Antes de construir la app completa, se implemento una primera base de transporte BLE en Kotlin.

Se crearon:

- configuracion de transporte
- modelos de `chunk`, `bloque` y `muestra`
- parser del chunk
- parser del bloque logico
- reensamblador por `packet_id`
- pipeline `chunk -> bloque`

### Objetivo de esta fase

Separar la logica del transporte de la capa Android visual y del BLE real.

Esto permitio:

- validar el contrato binario
- avanzar sin depender del firmware real
- preparar la logica para enchufarla mas adelante a `BluetoothGattCallback`

## 12. Construccion del scaffold Android

Posteriormente se construyo un scaffold Android real en:

- `app/mobile-android`

Incluyendo:

- proyecto Gradle
- modulo `app`
- `MainActivity`
- UI en Compose
- pantalla de demo
- serializacion del bloque
- fuente BLE simulada
- esqueleto de cliente BLE real

### Problema de estructura

En una primera iteracion, parte del codigo quedo repartido entre:

- `mobile-android/src/...`
- `mobile-android/app/src/...`

Esto se detecto como una fuente probable de problemas de sincronizacion de Gradle, y se corrigio moviendo todo a:

- `mobile-android/app/src/main/java`

## 13. Implementacion de una simulacion funcional

Se implemento una simulacion completa del flujo de datos con:

- fuente fake de notificaciones BLE
- generacion de bloques IMU sinteticos
- serializacion a bytes
- troceado en chunks
- reensamblado
- parseo del bloque
- visualizacion de contadores en UI

La pantalla de demo muestra:

- modo actual
- MTU asumido o negociado
- payload de chunk disponible
- numero de notificaciones vistas
- bloques completados
- ultimo `packet_id`
- ultimo `sample_count`

## 14. Incidencias de entorno y resolucion

Durante el arranque del proyecto aparecieron varias incidencias de entorno.

### 14.1. Falta de espacio en disco

El primer bloqueo importante fue:

- `Espacio en disco insuficiente`

Esto impedia a Gradle descargar dependencias y sincronizar el proyecto.

La solucion fue:

- liberar espacio en `C:`

### 14.2. Error de configuracion de Gradle

Mas adelante aparecio un error relacionado con:

- `debugRuntimeClasspathCopy`

Se investigo y se corrigieron dos causas probables:

- estructura de fuentes poco estandar
- uso de una configuracion temporal de `sourceSets`

### 14.3. Uso de Gradle preview

Tambien se detecto que el wrapper estaba usando:

- `gradle-9.0-milestone-1`

Al ser una version preview, se decidio bajar a una combinacion estable:

- Gradle `8.7`
- Android Gradle Plugin `8.5.2`
- Kotlin `1.9.24`

Este cambio permitio estabilizar la sincronizacion del proyecto.

### 14.4. Problema del emulador

La app llego a compilar correctamente, pero el emulador no pudo arrancar por:

- espacio insuficiente para crear la particion de datos del AVD

La alternativa elegida fue:

- usar un movil Android real

## 15. Primera ejecucion real en movil

Tras activar la depuracion USB y seleccionar un dispositivo Android real, se logro:

- compilar la app
- instalarla en el movil
- abrir la pantalla de demo

Posteriormente, al pulsar `Iniciar`, se verifico que:

- los numeros en pantalla comenzaban a cambiar

Eso confirma que el flujo simulado funciona de extremo a extremo:

- generacion de datos
- serializacion
- chunking
- reensamblado
- parseo
- actualizacion de UI

## 16. Estado actual alcanzado

En este punto del proyecto ya se ha conseguido:

- definir el MVP de la app
- justificar el stack tecnologico
- definir una primera version del transporte BLE
- documentar el razonamiento tecnico
- crear un scaffold Android funcional
- ejecutar la app en un movil real
- validar un flujo simulado completo de transporte y visualizacion

## 17. Decisiones que han cambiado o madurado

Durante el proceso hubo varias decisiones que evolucionaron:

### Tamano de bloque

Se paso de una idea inicial de `~2 s por bloque` a una propuesta de `0.5 s` para el primer MVP tecnico.

### Nivel de detalle del protocolo

Se paso de una idea general de envio por bloques a una especificacion mucho mas concreta con:

- cabecera
- payload
- chunking
- timeout
- reglas de error

### Tecnologia de la app

Se partio de una pregunta abierta sobre el stack y se consolido la decision de `Kotlin + Android nativo`.

### Entorno de prueba

Se partio de la idea de usar emulador, pero se termino validando que en este contexto era mas practico usar un movil real.

## 18. Valor para la memoria del TFG

Este proceso deja ya varias piezas reutilizables para la redaccion futura:

- justificacion de requisitos del MVP
- justificacion de stack tecnologico
- diseno del protocolo BLE
- argumentos sobre `MTU` y fragmentacion
- evidencia de decisiones iterativas y razonadas
- trazabilidad entre requisitos y prototipo funcional

## 19. Proximos pasos previstos

A partir del estado actual, las lineas mas naturales de trabajo son:

1. estructurar mejor la app
2. introducir persistencia local
3. crear pantallas mas cercanas al flujo final
4. integrar BLE real
5. conectar el wearable real al pipeline ya probado

La recomendacion actual es avanzar primero en:

- estructura base de la app
- persistencia local
- separacion entre demo tecnica y flujo funcional real

para despues conectar:

- escaneo BLE
- conexion real
- `requestMtu`
- `BluetoothGattCallback`

## 20. Evolucion posterior de la app

Tras validar el flujo tecnico end-to-end en una pantalla de demo, se decidio dar un siguiente paso orientado ya al producto:

- separar la demo tecnica interna del flujo principal de la app
- crear una estructura mas limpia
- incorporar pantallas base de `Conexion` y `Sesion`
- anadir persistencia local minima para sesiones simuladas

### Motivo de esta decision

Se considero preferible avanzar primero en:

- estructura de aplicacion
- flujo funcional
- persistencia local

antes de introducir BLE real.

El criterio ingenieril fue no mezclar demasiadas variables a la vez:

- una cosa es validar el transporte
- otra distinta es validar la arquitectura funcional del MVP
- y otra es integrar el wearable real

### Cambio de enfoque

La demo tecnica deja de ser la pantalla principal de la app y pasa a funcionar como:

- herramienta interna de validacion

mientras que la aplicacion principal se orienta ya a:

- conexion
- sesion
- guardado local
- evolucion hacia BLE real

## 21. Evidencias visuales registradas

Se han almacenado capturas de pantalla del prototipo en:

- `docs/evidencias/`

Estas evidencias sirven como apoyo para la redaccion de la memoria y permiten documentar de forma visual el estado alcanzado por la app en distintas fases.

### 21.1. Demo tecnica interna en estado inicial

Archivo:

- `docs/evidencias/2026-04-28_demo_tecnica_estado_inicial.jpeg`

Descripcion:

- muestra la pantalla de demo tecnica interna antes de iniciar la simulacion
- se observan los contadores a cero
- se confirma la presencia del modo simulado
- se visualiza el `MTU asumido/negociado` y el `payload chunk disponible`

Interpretacion:

- esta captura documenta que la app ya incorpora una pantalla interna dedicada a validar el transporte BLE binario sin depender todavia del wearable real

### 21.2. Demo tecnica con transporte simulado funcionando

Archivo:

- `docs/evidencias/2026-04-28_demo_tecnica_chunks_funcionando.jpeg`

Descripcion:

- muestra la demo tecnica una vez iniciada la simulacion
- aparecen contadores distintos de cero en:
  `Notificaciones vistas`, `Bloques completados`, `Ultimo packet_id` y `Ultimo sample_count`
- el estado indica un bloque completado con `52 muestras`

Interpretacion:

- esta evidencia confirma visualmente el funcionamiento del flujo:
  generacion de datos sinteticos -> serializacion -> fragmentacion en chunks -> reensamblado -> parseo -> actualizacion de interfaz

### 21.3. Pantalla de conexion del flujo principal

Archivo:

- `docs/evidencias/2026-04-28_pantalla_conexion_mvp.jpeg`

Descripcion:

- muestra la pantalla `Conexion` del flujo principal del MVP
- se reflejan el estado actual del desarrollo y los siguientes pasos funcionales previstos:
  escaneo BLE, solicitud de conexion, negociacion de MTU y suscripcion a notificaciones

Interpretacion:

- esta captura evidencia el paso desde una demo exclusivamente tecnica a una arquitectura de aplicacion orientada ya al producto final

### 21.4. Pantalla de sesion en estado inicial

Archivo:

- `docs/evidencias/2026-04-28_pantalla_sesion_estado_inicial.jpeg`

Descripcion:

- muestra la pantalla `Sesion` antes de iniciar ninguna captura
- todos los contadores aparecen a cero
- aun no existen sesiones guardadas en local

Interpretacion:

- esta evidencia documenta el punto inicial del flujo funcional de sesion y sirve como referencia para comparar el comportamiento antes y despues del registro simulado

### 21.5. Sesion simulada guardada correctamente en local

Archivo:

- `docs/evidencias/2026-04-28_sesion_simulada_guardada_local_ok.jpeg`

Descripcion:

- muestra la pantalla de sesion despues de ejecutar una captura simulada y detenerla
- se visualiza un resumen de la sesion con:
  numero de notificaciones, bloques completos, muestras y `ultimo packet_id`
- aparece la entrada correspondiente en `Sesiones guardadas`

Interpretacion:

- esta captura confirma que el flujo funcional de sesion ya permite:
  iniciar una sesion simulada, procesar los datos, detener la captura y persistir un resumen localmente

### Valor documental de las evidencias

En conjunto, estas capturas permiten justificar visualmente tres hitos del desarrollo:

1. validacion interna del transporte BLE simulado
2. estructuracion de la app en pantallas funcionales
3. persistencia local de sesiones simuladas

Esto aporta material muy util para la futura memoria, especialmente en los apartados de:

- implementacion
- validacion
- resultados del prototipo

## 22. Observaciones finales

El desarrollo realizado hasta ahora no se ha limitado a crear una interfaz visual, sino que ha construido una base tecnica coherente para la app del TFG:

- con decisiones razonadas
- con soporte documental
- con validacion en movil real
- y con un contrato de transporte pensado para conectarse despues al firmware

Este documento debe seguir actualizandose en las siguientes iteraciones para mantener trazabilidad del proceso completo.
