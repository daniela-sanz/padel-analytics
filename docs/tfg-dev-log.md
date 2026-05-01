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

## 2026-04-29 - Integracion BLE real minima en la pantalla de conexion

Se ha sustituido la pantalla de `Conexion` puramente informativa por un flujo BLE real minimo orientado a la prueba de humo con la XIAO. La app puede ahora:

- escanear dispositivos BLE filtrando por el servicio del smoke test
- listar dispositivos encontrados
- conectar con uno de ellos
- solicitar MTU
- suscribirse a notificaciones
- mostrar contador, bateria dummy, flag y payload hexadecimal recibido

Esta fase no pretende aun integrar datos IMU reales ni el protocolo final de chunks, sino validar la capa de enlace BLE de extremo a extremo antes de conectar el pipeline de transporte real.

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

## 21. Introduccion de persistencia estructurada con Room

Una vez validado el guardado local basico de sesiones simuladas, se identifico como siguiente paso logico la sustitucion de la persistencia ad hoc por una solucion mas estructurada.

Se decidio introducir `Room`, que actua como capa de abstraccion sobre `SQLite` en Android.

### Motivo de la decision

La persistencia previa basada en archivo servia para validar el flujo, pero presentaba limitaciones claras:

- poco escalable
- sin modelo relacional
- peor base para consultas futuras
- no preparada para crecer hacia bloques, muestras y eventos

Room aporta una base mas seria para la evolucion de la app y facilita el paso posterior a:

- detalle de sesiones
- almacenamiento de bloques
- almacenamiento de muestras
- consultas y filtros

### Alcance de esta primera integracion

En esta fase no se pretendio persistir aun todas las muestras IMU, sino establecer una base de datos formal para las sesiones.

Se definio:

- una entidad `SessionEntity`
- un `SessionDao`
- una base `AppDatabase`
- un repositorio `RoomSessionRepository`

### Enfoque adoptado

El cambio se realizo de forma incremental:

- se mantiene el flujo simulado actual
- se sustituye la persistencia basada en archivo por Room
- se prepara el terreno para una futura ampliacion del modelo de datos

Con ello, la app empieza a parecerse mas a una arquitectura real de producto y menos a un prototipo de validacion aislado.

## 22. Decision de producto y arquitectura del dashboard

Tras revisar el objetivo real del MVP, se decidio priorizar un `dashboard post-sesion` y no intentar construir desde el principio un dashboard completo en tiempo real.

### Decision adoptada

Durante la sesion, la app mostrara solo:

- telemetria basica
- estado de conexion
- estado de grabacion
- contadores
- metricas ligeras

El dashboard completo se construira:

- al finalizar la sesion
- a partir de los datos persistidos

### Justificacion

Esta decision se considero la mas adecuada para el MVP y para el TFG porque:

- es mas estable
- es mas facil de validar
- es mas defendible en la memoria
- reduce la complejidad del MVP
- separa mejor adquisicion, persistencia, procesado y visualizacion

### Lectura tecnica

Se distinguen dos modos:

#### Caso A - Sesion en curso

La app muestra una capa ligera de informacion:

- conectado o no
- grabando o no
- bateria
- bloques recibidos
- muestras recibidas
- posibles perdidas
- golpes candidatos
- pasos
- pico de aceleracion
- pico de giro

Estas metricas se han elegido porque son:

- faciles de calcular de forma incremental
- computacionalmente ligeras
- utiles para depurar el wearable y la comunicacion BLE
- suficientes para tener visibilidad durante pruebas sin intentar construir todavia el dashboard completo en vivo

#### Caso B - Post-sesion

La app:

- lee los datos persistidos
- procesa la sesion
- calcula KPIs
- genera graficas
- pinta el dashboard completo

### Arquitectura de datos recomendada

La direccion de almacenamiento a medio plazo queda asi:

- `Session` en `Room`
- `SessionBlock` en `Room`
- archivo crudo por sesion para IMU completa

Con ello:

- `Room` guarda estructura e indices
- los bloques quedan trazados por sesion
- la señal completa puede conservarse para reprocesado y exportacion
- el dashboard post-sesion puede reconstruirse de forma fiable

La interpretacion correcta de `Room / archivo` no es elegir uno de los dos, sino usar ambos con roles distintos:

- `Room` para sesiones, bloques, metadatos, estados y referencias
- `archivo crudo` para conservar la señal IMU completa por sesion

### Consecuencia practica

El orden recomendado de desarrollo pasa a ser:

1. persistencia estructurada
2. bloques por sesion
3. almacenamiento crudo por sesion
4. procesado post-sesion
5. dashboard completo
6. BLE real

## 23. Introduccion de SessionBlock en la persistencia local

Como siguiente paso de implementacion, se extendio el modelo local para que la app no guardase solo el resumen de una sesion, sino tambien cada bloque recibido durante la captura simulada.

### Objetivo

Dar trazabilidad a la sesion a un nivel intermedio entre:

- el mero resumen de sesion
- y el almacenamiento futuro de toda la señal cruda

### Cambio realizado

Se introdujo una nueva entidad:

- `SessionBlockEntity`

asociada a `SessionEntity` mediante `sessionId`.

Cada bloque guarda, al menos:

- `packetId`
- `timestampBlockStartMs`
- `sampleStartIndex`
- `sampleCount`
- `stepCountTotal`
- `batteryLevelPercent`
- `statusFlags`
- `receivedAtEpochMs`

### Valor arquitectonico

Esto permite:

- saber que bloques concretos pertenecen a cada sesion
- comprobar si la persistencia intermedia funciona correctamente
- dejar preparada la app para una futura vista de detalle de sesion
- acercar la arquitectura al modelo previsto de `Session + SessionBlock + archivo crudo`

### Limitacion actual

Todavia no se almacenan las muestras IMU completas ni el bloque crudo serializado.

Este paso se considera una capa intermedia deliberada antes de introducir:

- archivo crudo por sesion
- procesado post-sesion
- dashboard completo

### Ajuste posterior en la persistencia de sesiones

Durante la validacion de `SessionBlock` se detecto un comportamiento incorrecto:

- el numero de bloques persistidos aparecia como `0`

La causa fue que la sesion se estaba guardando al final mediante una operacion tipo `REPLACE`, lo que en la practica implicaba borrar y recrear la fila padre.

Dado que los bloques estaban relacionados con la sesion mediante `foreign key` con borrado en cascada, ese reemplazo eliminaba tambien los `SessionBlock` asociados.

La correccion adoptada fue:

- `insert` al crear la sesion
- `update` al cerrar la sesion

Con ello se evita borrar la fila padre y se preservan correctamente los bloques ya guardados.

## 24. Introduccion del archivo crudo por sesion

Como siguiente paso, se anadio almacenamiento crudo por sesion para conservar la señal IMU completa de cada captura simulada.

### Enfoque elegido

Se opto por un archivo `CSV` por sesion, generado mientras la sesion esta activa.

La motivacion fue:

- facilidad de depuracion
- facilidad de inspeccion manual
- utilidad futura para exportacion
- simplicidad en esta fase del MVP

### Contenido del archivo

Cada fila representa una muestra IMU e incluye, entre otros campos:

- `session_id`
- `packet_id`
- `block_timestamp_ms`
- `sample_global_index`
- `sample_index_in_block`
- `step_count_total`
- `battery_level`
- `status_flags`
- `ax, ay, az, gx, gy, gz`

### Valor de este paso

Con esta incorporacion, la app ya dispone de:

- `Session` en `Room`
- `SessionBlock` en `Room`
- archivo crudo por sesion

Esto completa la base tecnica necesaria para construir mas adelante:

- detalle de sesion
- procesado post-sesion
- exportacion
- dashboard analitico

## 25. Vista basica de detalle de sesion para validacion

Antes de conectar BLE real, se considero conveniente anadir una vista minima de detalle de sesion para inspeccionar lo que ya se estaba guardando.

### Objetivo

Poder comprobar desde la propia app que:

- la sesion existe
- los bloques se persisten en `Room`
- el archivo crudo se genera realmente
- las primeras muestras del archivo tienen contenido coherente

### Solucion adoptada

Se introdujo una vista de detalle ligera dentro de la pantalla de sesiones que permite:

- seleccionar una sesion guardada
- consultar cuantos bloques tiene en base de datos
- visualizar un pequeno resumen de los primeros bloques
- leer una previsualizacion de las primeras filas del archivo CSV crudo

### Motivo de la decision

Esta vista no es todavia el dashboard final, pero cumple una funcion de validacion muy importante:

- reduce incertidumbre antes de conectar la pulsera real
- permite depurar el pipeline completo de persistencia
- da trazabilidad visual al estado interno de una sesion

### Relacion con el roadmap

Este paso encaja como puente entre:

- persistencia estructurada
- y futuro procesado post-sesion

de forma que la app ya puede inspeccionar lo que guarda antes de entrar en analitica mas compleja.

## 26. Evidencias visuales registradas

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

### 21.6. Detalle de sesion con bloques en Room y previsualizacion del crudo

Archivo:

- `docs/evidencias/2026-04-29_detalle_sesion_room_y_crudo_ok.jpeg`

Descripcion:

- muestra la pantalla de sesion con una vista de detalle abierta
- se visualiza el numero de bloques guardados en `Room`
- aparecen los primeros bloques con sus metadatos
- se muestra una previsualizacion de las primeras muestras leidas desde el archivo crudo CSV
- se mantiene el resumen general de la sesion en la parte inferior

Interpretacion:

- esta evidencia confirma visualmente que la app ya puede:
  persistir la sesion, persistir los bloques, generar el archivo crudo y leerlo de vuelta para inspeccion

### Valor documental de las evidencias

En conjunto, estas capturas permiten justificar visualmente cuatro hitos del desarrollo:

1. validacion interna del transporte BLE simulado
2. estructuracion de la app en pantallas funcionales
3. persistencia local de sesiones simuladas
4. inspeccion de detalle de sesion con `Room + SessionBlock + archivo crudo`

Esto aporta material muy util para la futura memoria, especialmente en los apartados de:

- implementacion
- validacion
- resultados del prototipo

## 22. Introduccion de un procesador post-sesion

Tras validar que la app ya podia:

- guardar una `Session`
- guardar `SessionBlock`
- generar un archivo crudo CSV por sesion
- leer de vuelta ese archivo para una previsualizacion

se considero que el siguiente paso logico ya no era BLE real, sino empezar a transformar los datos guardados en metricas utiles.

### Decision adoptada

Se decidio construir una primera capa de procesado post-sesion basada en:

- lectura del CSV crudo de una sesion
- calculo de metricas sencillas y explicables
- visualizacion de esas metricas dentro del detalle de sesion

Esta decision sigue la estrategia acordada previamente:

- durante sesion: telemetria ligera
- despues de sesion: analitica y dashboard serio

### Motivo para no pasar todavia a BLE real

Se considero preferible cerrar antes la cadena completa de la app:

1. recibir datos
2. guardarlos
3. releerlos
4. procesarlos
5. mostrarlos

De esta forma, cuando se conecte el wearable real, la mayor parte del pipeline de la app ya estara validado y el unico cambio importante sera la fuente de datos.

## 23. Separacion entre parseo de CSV y procesado

Para no mezclar responsabilidades, se introdujo una separacion explicita:

- `data/raw`: lectura y parseo del archivo crudo
- `data/processing`: calculo de metricas post-sesion

### Archivos nuevos creados

- `data/raw/RawCsvSampleRecord.kt`
- `data/raw/SessionRawCsvRowParser.kt`
- `data/processing/PostSessionSummary.kt`
- `data/processing/PostSessionProcessor.kt`

### Objetivo de esta separacion

- evitar duplicar parseo del CSV
- reutilizar la misma interpretacion de filas en preview y procesado
- dejar una base limpia para futuros KPIs
- hacer el codigo mas facil de explicar en la memoria

## 24. Que hace PostSessionProcessor

La clase `PostSessionProcessor` lee el archivo CSV crudo de una sesion y calcula un primer resumen post-sesion.

### Metricas implementadas en esta iteracion

- numero de muestras procesadas
- numero de `packet_id` distintos
- duracion estimada a partir de timestamps crudos
- pico de magnitud de aceleracion en unidades raw
- pico de magnitud de giro en unidades raw
- media de magnitud de aceleracion en unidades raw
- media de magnitud de giro en unidades raw
- numero de golpes candidatos
- bateria al inicio y al final del archivo

### Importante sobre las unidades

Estas metricas usan por ahora:

- valores `raw` del sensor
- no unidades fisicas finales calibradas

Esto se considero aceptable en esta fase porque el objetivo actual es:

- validar el pipeline de procesado
- disponer de metricas comparables
- tener herramientas de depuracion y trazabilidad

## 25. Criterio usado para golpes candidatos

Todavia no se implementa una clasificacion real de golpes.

En su lugar, se introdujo una heuristica barata computacionalmente:

- si la magnitud de aceleracion supera un umbral raw
- o si la magnitud de giro supera un umbral raw
- y ha pasado una ventana minima desde el ultimo evento
- entonces se incrementa el contador de `golpes candidatos`

### Justificacion

Esta metrica no pretende ser un KPI final de producto.

Su utilidad inmediata es:

- depurar sesiones
- comprobar que la senal tiene eventos destacados
- preparar una futura metrica mas elaborada
- aportar telemetria interpretable durante el desarrollo

## 26. Integracion en la vista de detalle

El resultado del procesado se integra dentro de la vista de detalle de sesion.

Eso permite ver en una sola pantalla:

- resumen de sesion guardado en `Room`
- bloques persistidos
- nombre del archivo crudo
- primeras filas del CSV
- y ahora tambien un resumen procesado post-sesion

Con ello la app ya cubre un flujo muy relevante:

1. generar sesion simulada
2. guardar estructura
3. guardar crudo
4. releer crudo
5. calcular metricas
6. mostrar resultado

## 27. Estado tras esta iteracion

En este punto, la app ya dispone de los siguientes niveles de persistencia y explotacion:

- `Session` en `Room`
- `SessionBlock` en `Room`
- archivo crudo CSV por sesion
- vista de detalle
- procesado post-sesion basico

Esto deja preparado el terreno para las siguientes fases probables:

- ampliar el conjunto de KPIs
- crear una pantalla de dashboard post-sesion
- integrar BLE real reutilizando el pipeline ya validado

## 28. Incorporacion de una pantalla de dashboard y preparacion de sesion

Una vez validado el primer `PostSessionProcessor`, se dio un paso hacia una interfaz mas cercana a producto.

### Objetivo de este cambio

Se buscaba dejar de mostrar solo datos tecnicos y empezar a representar:

- un `dashboard post-sesion` con aspecto mas deportivo y tecnologico
- una capa previa de `preparacion de sesion` con datos de contexto del usuario

### Dashboard post-sesion

Se anadio una nueva pantalla `Dashboard` dentro de la app.

Esta pantalla:

- consume la sesion seleccionada en detalle
- reutiliza el resultado del `PostSessionProcessor`
- muestra tarjetas con metricas clave
- da una lectura mas visual y menos tecnica del resumen de sesion

Las metricas representadas en esta primera version son:

- muestras procesadas
- bloques persistidos
- packets distintos
- impactos candidatos
- pico de aceleracion raw
- pico de giro raw
- bateria inicio-fin

### Extension posterior: vista casi en vivo durante sesion

Despues de esta primera version surgio la necesidad de no obligar siempre a cerrar la sesion para alimentar el dashboard.

Se decidio una solucion intermedia eficiente:

- no leer el CSV cada pocos segundos
- no reconsultar `Room` continuamente
- mantener un acumulador en memoria mientras llegan bloques
- publicar una instantanea ligera del dashboard cada `2 s`

Esta frecuencia se considero un buen compromiso para el MVP:

- suficiente sensacion de actualizacion
- bajo coste computacional
- sin castigar almacenamiento ni parseos repetidos

Tambien se anadio una primera interpretacion textual simple para acercar la vista al concepto de dashboard y no dejarla solo en forma de tabla tecnica.

### Preparacion de sesion

Dentro de la pantalla `Sesion` se anadio una seccion nueva para recoger datos de entrada del jugador antes de grabar.

Los campos introducidos en esta fase son:

- nombre o alias
- sexo
- mano dominante
- nivel
- notas previas de sesion

### Alcance real de esta preparacion

En esta iteracion estos datos:

- viven en el `SessionUiState`
- pueden editarse desde interfaz
- se reutilizan en el dashboard

pero todavia:

- no se persisten en `Room`
- no forman parte del modelo definitivo de sesion

Se considero aceptable porque el objetivo actual es validar el flujo de producto y la experiencia de usuario antes de endurecer la persistencia final.

### Archivos principales introducidos o ampliados

- `feature/dashboard/DashboardScreen.kt`
- `feature/session/SessionSetupUiState.kt`
- `feature/session/SessionScreen.kt`
- `feature/session/SessionUiState.kt`
- `feature/session/SessionViewModel.kt`
- `app/WearableAppRoot.kt`

### Valor de esta iteracion

Este cambio es importante porque desplaza la app un paso mas desde:

- herramienta de validacion tecnica

hacia:

- prototipo funcional con narrativa de producto

La app ya no solo puede recibir, guardar y procesar, sino tambien presentar la sesion de una forma mas cercana a lo que acabaria viendo un usuario final.

## 29. Separacion entre perfil del jugador y nombre de sesion

En una iteracion posterior se detecto que la pantalla `Sesion` estaba acumulando demasiada informacion en el cuerpo principal.

Se decidio separar dos conceptos:

- `perfil del jugador`: persistente y poco cambiante
- `nombre de sesion`: contextual y especifico de cada captura

### Cambios introducidos

#### Perfil del jugador

Los datos del jugador pasaron a un acceso en la parte superior derecha de la app, representado como un boton circular de perfil.

Desde ese dialogo se pueden editar:

- nombre o alias
- sexo
- mano dominante
- nivel
- notas

Estos datos ya no dependen de una sesion concreta y se guardan en preferencias locales para mantenerse entre aperturas de la app.

#### Nombre de sesion

La pantalla `Sesion` conserva un unico bloque de preparacion corta para nombrar la captura.

Ese nombre:

- se guarda en `Room` junto a la sesion
- aparece en la lista de sesiones
- aparece en el detalle de sesion
- aparece en el dashboard

Si el usuario no introduce nombre, se genera uno automatico con fecha y hora.

### Motivo de la separacion

Esta decision mejora tres aspectos:

- limpieza de interfaz
- claridad conceptual del modelo de datos
- cercania a un flujo de producto real

El usuario ya no tiene que reintroducir constantemente informacion del jugador que apenas cambia, mientras que cada sesion sigue pudiendo etiquetarse de forma concreta.

## 30. Unificacion visual con el dashboard

Tras introducir el dashboard con una estetica mas deportiva y tecnica, se detecto que el resto de pantallas seguian teniendo una apariencia demasiado neutra.

Se decidio por tanto unificar visualmente:

- `Conexion`
- `Sesion`
- `Demo tecnica`
- barra superior e inferior de navegacion

### Direccion visual adoptada

- fondo oscuro con gradiente
- paneles tecnicos oscuros
- textos claros con acentos cian y verde
- botones primarios mas energeticos

### Objetivo

No se trataba solo de embellecer la app, sino de:

- dar coherencia al prototipo
- acercarlo mas a un producto identificable
- reforzar el caracter `tech + deportivo` del TFG

## 31. Observaciones finales

El desarrollo realizado hasta ahora no se ha limitado a crear una interfaz visual, sino que ha construido una base tecnica coherente para la app del TFG:

- con decisiones razonadas
- con soporte documental
- con validacion en movil real
- y con un contrato de transporte pensado para conectarse despues al firmware

Este documento debe seguir actualizandose en las siguientes iteraciones para mantener trazabilidad del proceso completo.

## 32. Preparacion de chunks BLE reales v1

Tras validar discovery, conexion y notificaciones BLE reales con un payload de humo de 6 bytes, se dio el siguiente paso: introducir un sketch que ya envia chunks con la cabecera real del transporte.

### Decision importante

Se mantiene el formato conceptual del protocolo v1, pero se reduce temporalmente el tamano efectivo del bloque para adaptarlo al `MTU=23` observado en la prueba real.

### Perfil diagnostico elegido

- `protocol_version = 1`
- `block_type = 1`
- `4 muestras fake por bloque`
- `74 bytes por bloque logico`
- `11 bytes de payload util por chunk`
- `7 chunks por bloque`

### Motivo

Con un `MTU negociado = 23`, la notificacion util disponible es aproximadamente de `20 bytes`. Como el header de chunk ocupa `9 bytes`, el payload util del chunk queda reducido a `11 bytes`.

En esta fase se prioriza:

- validar la cabecera de chunk
- validar endianess
- validar `chunk_index / chunk_count`
- validar reensamblado en Android
- validar parser del bloque logico

sin mezclar todavia la IMU real.

### Adaptacion de la app Android

La pantalla `Conexion` se ha ampliado para detectar dos modos de transporte:

- `Smoke payload`
- `Chunk v1`

En el segundo caso muestra:

- chunks recibidos
- `packet_id` del ultimo chunk
- bloques reensamblados
- `packet_id` y `sample_count` del ultimo bloque completo

Esto convierte la propia pantalla de conexion en una herramienta de depuracion BLE mucho mas util antes de enchufar la sesion productiva.

## 33. Validacion BLE real del flujo chunk v1

La fase BLE real puede considerarse cerrada a nivel de transporte minimo porque ya se ha validado en movil real:

- descubrimiento de la XIAO
- conexion BLE real
- recepcion sostenida de notificaciones
- deteccion de `Chunk v1`
- reensamblado correcto de bloques logicos en Android

### Evidencias visuales asociadas

Archivos:

- `docs/evidencias/2026-04-30_ble_real_scan_xiao_chunk_v1_ok.jpeg`
- `docs/evidencias/2026-04-30_ble_real_chunk_v1_reensamblado_ok.jpeg`

Interpretacion:

- la primera captura demuestra que la app descubre el emisor BLE real `XIAO-Padel-ChunkV1`
- la segunda demuestra que la app recibe chunks reales, cambia a `Modo transporte = Chunk v1` y reensambla bloques completos

### Hito tecnico alcanzado

Con esta validacion quedan confirmados tres puntos clave:

1. `BLE real validado`
2. `chunking real validado`
3. `reensamblado Android validado`

## 34. Paso de muestras fake a IMU real sin cambiar el transporte

Una vez validado el contrato `Chunk v1`, se decide no tocar todavia la capa BLE ni el formato del bloque logico. El siguiente cambio se limita a sustituir la fuente de datos:

- antes: muestras IMU fake generadas en firmware
- ahora: muestras IMU reales leidas desde el `LSM6DS3`

### Decision adoptada

Se crea un sketch nuevo:

- `arduino/BLE_chunk_sender_v1_imu/BLE_chunk_sender_v1_imu.ino`

La idea de este sketch es deliberadamente conservadora:

- mantiene `protocol_version = 1`
- mantiene el mismo header de chunk
- mantiene el mismo header de bloque logico
- mantiene el mismo perfil diagnostico de `4 muestras por bloque`
- mantiene `7 chunks por bloque`

Lo unico que cambia es el origen de las muestras, que dejan de ser sinteticas y pasan a capturarse desde la IMU real de la XIAO.

### Motivo de este enfoque

Se considera la forma mas limpia de avanzar porque permite aislar variables:

- el transporte ya esta validado
- el parser Android ya esta validado
- el reensamblado ya esta validado

Por tanto, si apareciesen incidencias nuevas, quedarian acotadas casi por completo a:

- lectura del sensor
- temporizacion de muestreo
- coherencia de los valores raw reales

### Perfil temporal mantenido

Aunque el objetivo final del MVP sigue siendo:

- `52 muestras por bloque`
- `650 bytes por bloque logico`
- aproximadamente `4 chunks` por bloque si el MTU lo permite

en esta fase se mantiene el perfil reducido porque el `MTU negociado` observado en la validacion real fue `23`.

Por ello se sigue trabajando temporalmente con:

- `4 muestras por bloque`
- `74 bytes por bloque logico`
- `11 bytes utiles por chunk`
- `7 chunks por bloque`

### Resultado esperado de la siguiente prueba

Si el nuevo sketch funciona correctamente, la app deberia seguir mostrando:

- `Modo transporte = Chunk v1`
- chunks recibidos creciendo
- bloques reensamblados creciendo
- `Ultimo bloque muestras = 4`

pero ahora con valores de acelerometro y giroscopio procedentes ya del IMU fisico real.
