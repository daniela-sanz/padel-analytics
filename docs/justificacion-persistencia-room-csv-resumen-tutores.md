# Resumen para tutores: persistencia Room + CSV

## Objetivo

La estrategia de persistencia de la app se ha planteado como una solucion hibrida:

- `Room` para la informacion estructurada
- `CSV` para la senal IMU cruda completa

La idea no es mezclar tecnologias sin criterio, sino asignar a cada una el papel que mejor resuelve dentro del MVP.

## Por que usar Room

`Room` se utiliza para almacenar la parte estructurada de la sesion, por ejemplo:

- sesiones
- bloques
- metadatos
- referencias al archivo crudo

Esto permite:

- listar sesiones guardadas
- consultar detalles de una sesion
- contar bloques
- enlazar la sesion con su fichero crudo

En otras palabras, `Room` resuelve bien la parte relacional y consultable del sistema.

## Por que usar CSV para la senal cruda

La senal IMU completa tiene otra naturaleza:

- mucho volumen
- estructura repetitiva
- interes principal en lectura secuencial y reprocesado

Guardar este dato crudo en `CSV` aporta ventajas claras para el TFG:

- facilita la depuracion
- permite revisar continuidad de `packet_id` y `sample_index`
- deja una evidencia experimental facil de inspeccionar
- simplifica la exportacion y el analisis externo

## Por que no guardar todo solo en Room

Persistir todas las muestras IMU como filas individuales en base de datos habria supuesto:

- mas complejidad del modelo
- mas coste de insercion
- mas peso relacional
- menos simplicidad para el MVP

No se descarta que en una evolucion futura pudiera plantearse otra solucion, pero no era la opcion mas razonable para esta fase.

## Por que no guardar todo solo en archivos

Si todo estuviera solo en archivos, la app perderia comodidad para:

- listar sesiones
- consultar resumenes
- navegar por sesiones guardadas
- relacionar captura, bloques y dashboard

Por eso una solucion exclusivamente basada en ficheros tampoco se considero suficiente.

## Sentido de la solucion hibrida

La separacion final queda asi:

- `Room`: estructura, indices, metadatos y consultas
- `CSV`: senal completa, validacion y reprocesado

Esta arquitectura permite:

- claridad de diseno
- trazabilidad
- facilidad de depuracion
- una base razonable para crecer

## Conclusion

La decision `Room + CSV` se considera adecuada para el MVP porque equilibra:

- simplicidad de implementacion
- utilidad practica para la app
- validacion experimental del TFG
- posibilidad de evolucion posterior

Por tanto, no se trata de una solucion provisional improvisada, sino de una decision de diseno coherente con el tipo de datos que maneja el sistema y con los objetivos del proyecto.
