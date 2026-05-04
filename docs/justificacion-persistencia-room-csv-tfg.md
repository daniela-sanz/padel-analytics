# Justificacion de la estrategia de persistencia: Room + dato crudo CSV

## 1. Objetivo del apartado

Este apartado justifica la estrategia de persistencia adoptada en la aplicacion Android del proyecto. En concreto, se explica por que se ha optado por una solucion hibrida basada en:

- `Room` para la informacion estructurada de sesiones y bloques
- archivo crudo `CSV` para la senal IMU completa

La eleccion no responde a una preferencia arbitraria, sino a la necesidad de equilibrar:

- simplicidad de implementacion
- trazabilidad de los datos
- capacidad de consulta
- facilidad de depuracion
- posibilidad de reprocesado posterior

## 2. Necesidad de una persistencia dual

El sistema genera dos tipos de informacion claramente distintos:

### 2.1 Informacion estructurada

Se trata de datos con una semantica clara y relativamente estable, tales como:

- sesion
- identificador de sesion
- fecha
- nombre de la sesion
- numero de bloques
- metadatos de resumen
- referencias a archivos asociados

Este tipo de informacion encaja naturalmente en un modelo relacional, ya que:

- puede indexarse
- puede consultarse
- puede filtrarse
- puede reutilizarse facilmente en la interfaz

### 2.2 Senal cruda de alta frecuencia

La senal IMU completa tiene una naturaleza diferente:

- gran volumen de datos
- estructura repetitiva
- uso intensivo en lectura secuencial
- relevancia para inspeccion y reprocesado

No se trata de un conjunto de entidades de negocio independientes, sino de una secuencia temporal de muestras que interesa conservar de forma integra y accesible.

## 3. Justificacion de Room para la parte estructurada

`Room` se eligio como capa de persistencia estructurada sobre `SQLite` por varios motivos.

### 3.1 Integracion natural con Android

`Room` forma parte del ecosistema oficial de Android y proporciona:

- integracion directa con Kotlin
- seguridad de tipos
- anotaciones declarativas
- consultas SQL controladas
- una base estable para evolucion futura

Esto lo convierte en una solucion muy adecuada para un MVP Android nativo.

### 3.2 Utilidad para sesiones y bloques

En el proyecto, `Room` resulta especialmente adecuado para almacenar:

- `Session`
- `SessionBlock`
- estados
- contadores
- referencias al archivo crudo

Estos datos son:

- discretos
- consultables
- utiles para poblar listas, detalles y dashboard

Por tanto, `Room` permite representar la estructura logica de la sesion de una forma clara y mantenible.

### 3.3 Facilidad de explotacion en la interfaz

Una vez almacenados en `Room`, estos datos pueden utilizarse facilmente para:

- listar sesiones guardadas
- abrir un detalle de sesion
- contar bloques
- reconstruir resumenes
- enlazar la sesion con su archivo crudo

Esto simplifica notablemente la capa de aplicacion y evita que toda la logica de consulta dependa de leer archivos completos.

## 4. Por que no guardar toda la senal IMU solo en Room

A priori podria parecer razonable almacenar tambien todas las muestras IMU dentro de la base de datos. Sin embargo, esta opcion no se considero la mejor para el MVP.

### 4.1 Volumen elevado

La senal cruda se genera a alta frecuencia. En este proyecto se ha trabajado con:

- `104 Hz`

Cada muestra contiene:

- `ax`
- `ay`
- `az`
- `gx`
- `gy`
- `gz`

Por tanto, incluso en sesiones relativamente cortas, el numero de filas crece rapidamente. Guardar todas las muestras como registros individuales en base de datos implica:

- mayor volumen de escritura
- mayor coste de insercion
- mayor complejidad del modelo
- mayor peso del esquema relacional

### 4.2 Naturaleza secuencial del dato

La senal IMU cruda suele usarse principalmente de forma:

- secuencial
- temporal
- masiva

Es decir, interesa leer el tramo completo o una parte grande del mismo para:

- validacion
- exportacion
- reprocesado
- calculo de metricas

Ese patron de uso no explota especialmente bien las ventajas relacionales de una base de datos ligera como la del MVP.

### 4.3 Aumento innecesario de complejidad

Persistir toda la senal en `Room` desde el primer momento habria obligado a definir:

- mas entidades
- mas relaciones
- mas DAOs
- mas estrategias de insercion
- mas consultas

Esto habria complicado el MVP antes de validar si realmente era necesario.

## 5. Justificacion del archivo crudo CSV

Para la senal completa se eligio una persistencia en archivo crudo, inicialmente en formato `CSV`.

### 5.1 Ventaja principal: trazabilidad directa

El archivo `CSV` permite:

- inspeccionar facilmente las muestras
- comprobar continuidad de `packet_id`
- comprobar continuidad de `sample_global_index`
- revisar timestamps
- validar contenido real sin depender de capas intermedias

Esto es especialmente util en una fase de TFG donde la trazabilidad y la validacion manual tienen mucho peso.

### 5.2 Facilidad de exportacion y analisis externo

El `CSV` puede abrirse directamente en:

- hojas de calculo
- scripts de analisis
- herramientas de validacion
- pipelines de procesamiento posteriores

Por tanto, resulta muy conveniente para:

- depuracion
- evidencia experimental
- analisis offline
- futuras comparaciones

### 5.3 Simplicidad de implementacion

Guardar la senal cruda en `CSV` evita introducir demasiado pronto una infraestructura mas compleja de almacenamiento binario o serializacion avanzada.

Para el MVP, esto aporta varias ventajas:

- implementacion mas simple
- lectura humana inmediata
- facilidad para generar evidencias del proyecto
- menor friccion para inspeccion durante el desarrollo

## 6. Por que no guardar todo solo en archivos

La alternativa opuesta, es decir, guardar tanto metadatos como datos crudos exclusivamente en archivos, tampoco se considero adecuada.

### 6.1 Menor capacidad de consulta estructurada

Si toda la informacion estuviese solo en archivos, operaciones como:

- listar sesiones
- buscar por nombre
- recuperar rapidamente un resumen
- conocer cuántos bloques tiene una sesion

serian mas costosas y menos limpias.

### 6.2 Menor claridad del modelo

Separar conceptualmente:

- sesion
- bloques
- resumen
- archivo crudo

resulta mucho mas natural con una base estructurada como `Room`.

### 6.3 Peor base para evolucion futura

La app no debe quedarse solo en una herramienta de exportacion. Tambien necesita:

- detalle de sesion
- dashboard
- metadatos reutilizables
- relacion clara entre captura, procesado y visualizacion

Solo con archivos, esa evolucion seria menos ordenada.

## 7. Sentido de la solucion hibrida

La estrategia finalmente adoptada combina lo mejor de ambos enfoques.

### 7.1 Rol de Room

`Room` se utiliza para:

- representar la estructura logica de la sesion
- guardar resumenes y metadatos
- indexar la informacion relevante
- permitir consultas rapidas desde la app

### 7.2 Rol del CSV

El `CSV` se utiliza para:

- conservar la senal completa
- facilitar validacion experimental
- permitir reprocesado posterior
- simplificar exportacion e inspeccion

### 7.3 Beneficio combinado

Esta separacion aporta:

- claridad arquitectonica
- eficiencia suficiente para el MVP
- buena trazabilidad
- facilidad de depuracion
- base razonable para crecer

## 8. Relacion con el dashboard y el procesado post-sesion

La solucion `Room + CSV` encaja bien con el flujo de la app:

1. durante la sesion se reciben bloques
2. se persiste la estructura en `Room`
3. se escribe la senal cruda en el archivo `CSV`
4. al finalizar, la app puede releer el crudo
5. se calculan metricas y KPIs
6. se alimenta el dashboard post-sesion

Esto permite mantener separadas las responsabilidades:

- persistencia estructurada
- almacenamiento de la senal
- procesado analitico
- visualizacion

## 9. Limitaciones asumidas

La eleccion de `CSV` como formato del crudo tambien tiene limitaciones:

- ocupa mas que un formato binario
- no es la opcion mas eficiente en almacenamiento
- no es la opcion mas rapida para grandes volumenes

Sin embargo, para el contexto actual del TFG, estas limitaciones son asumibles porque se priorizan:

- simplicidad
- auditabilidad
- evidencia experimental
- facilidad de trabajo durante el desarrollo

Si en una evolucion futura del sistema se necesitase mayor eficiencia, podria plantearse una migracion a:

- formato binario propio
- serializacion mas compacta
- almacenamiento crudo diferente

Pero no era una prioridad del MVP.

## 10. Conclusion

La estrategia de persistencia adoptada se considera adecuada para el alcance del proyecto porque:

- `Room` resuelve de forma limpia la parte estructurada
- `CSV` resuelve de forma practica la conservacion de la senal completa
- la combinacion de ambos reduce complejidad innecesaria
- la arquitectura resultante es explicable, trazable y defendible en la memoria

En consecuencia, la decision de utilizar:

- `Room` para sesiones y bloques
- `CSV` para la IMU cruda

no se entiende como una solucion provisional improvisada, sino como una decision de diseno coherente con los objetivos tecnicos y academicos del TFG.
