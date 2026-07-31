# MediSalud · Sistema de agendamiento de citas médicas

API REST para reservar, consultar, cancelar y reprogramar citas médicas, con cálculo de
disponibilidad y penalización de cancelaciones tardías.

Construido con **Java 21** y **Spring Boot 3.5**, con base de datos en memoria para poder
levantarlo con un solo comando y sin instalar nada más.

---

## Índice

1. [Ejecución local](#1-ejecución-local)
2. [Arquitectura](#2-arquitectura)
3. [Tecnologías](#3-tecnologías)
4. [Supuestos y ambigüedades resueltas](#4-supuestos-y-ambigüedades-resueltas)
5. [Endpoints](#5-endpoints)
6. [Tests](#6-tests)
7. [Docker y despliegue](#7-docker-y-despliegue)
8. [Limitaciones conocidas](#8-limitaciones-conocidas)

---

## 1. Ejecución local

### Requisitos

| | |
|---|---|
| JDK | 21 o superior |
| Maven | **no hace falta** — el proyecto incluye el wrapper |

Tampoco hace falta instalar base de datos: la aplicación arranca con H2 en memoria.

### Arrancar

```bash
./mvnw spring-boot:run          # Linux y macOS
mvnw.cmd spring-boot:run        # Windows
```

Eso es todo. El wrapper descarga Maven la primera vez, y el perfil `dev` está activo por
defecto (`spring.profiles.default` en `application.yml`), así que no hay que pasar ningún
parámetro. Si prefieres tu propio Maven (3.9+), `mvn spring-boot:run` funciona igual.

También se puede empaquetar y ejecutar el jar directamente:

```bash
./mvnw clean package
java -jar target/agendamiento-citas-1.0.0-SNAPSHOT.jar
```

La aplicación queda escuchando en `http://localhost:8080`.

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Documento OpenAPI | http://localhost:8080/v3/api-docs |
| Consola H2 | http://localhost:8080/h2-console |

Para la consola H2: JDBC URL `jdbc:h2:mem:medisalud_dev`, usuario `sa`, sin contraseña.

### Datos de ejemplo

Al arrancar se precargan automáticamente los tres médicos del enunciado:

| id | Nombre | Especialidad | Teléfono | Email |
|---|---|---|---|---|
| 1 | Dra. María González | Cardiología | 555-1001 | maria.gonzalez@medisalud.com |
| 2 | Dr. Carlos Ruiz | Pediatría | 555-1002 | carlos.ruiz@medisalud.com |
| 3 | Dra. Ana López | Dermatología | 555-1003 | ana.lopez@medisalud.com |

**Se cargan con un `CommandLineRunner` y no con `data.sql`.** Razones:

- Los datos pasan por el mismo mapeo JPA que el resto de la aplicación, así que un cambio en
  las entidades rompe la compilación en vez de fallar en tiempo de ejecución con un SQL
  desalineado.
- `data.sql` obliga a coordinar su ejecución con `ddl-auto` mediante
  `spring.jpa.defer-datasource-initialization`, un detalle fácil de romper.
- La carga es idempotente y condicional (`if (count > 0) return`), algo que un script plano
  no puede expresar sin SQL específico del motor.
- Al migrar a PostgreSQL el código sigue siendo válido sin reescribir SQL.

Está excluida del perfil `test` con `@Profile("!test")`, para que la suite controle sus
propios datos y las aserciones sobre listados no dependan de estos registros.

### Perfiles

| Perfil | Dónde vive | Para qué |
|---|---|---|
| `dev` | `src/main/resources/application-dev.yml` | Ejecución local. **Activo por defecto.** H2 `medisalud_dev`, `ddl-auto: create-drop`, SQL visible en el log, consola H2 abierta. |
| `test` | `src/test/resources/application-test.yml` | Suite automatizada. H2 `medisalud_test` (base distinta, para que correr los tests no toque los datos con los que estés probando a mano), logging silenciado, estadísticas de Hibernate activas. |
| `docker` | `src/main/resources/application-docker.yml` | El que usa la imagen. Mismo H2 en memoria, pero **sin consola H2 y sin volcado de SQL**, y con las credenciales por variable de entorno. |

> **Por qué existe el perfil `docker`.** El perfil `dev` deja abierta la consola H2 en
> `/h2-console`, que ejecuta SQL arbitrario contra la base sin pedir credenciales. En local
> es cómodo; en un contenedor publicado sería una puerta abierta a los datos de todos los
> pacientes. Como `spring.profiles.default` es `dev`, un contenedor que no fijara perfil
> explícitamente acabaría justo ahí. `ConfiguracionDeDespliegueTest` comprueba que el
> `Dockerfile` activa `docker` y nunca `dev`.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # explícito, equivale al comando de arriba
```

`application-test.yml` vive en `src/test/resources` y no en `main` a propósito: es
configuración de la suite, no de la aplicación, y no debe viajar dentro del artefacto
desplegable.

---

## 2. Arquitectura

### Capas

```
controller/   REST. Traduce HTTP ↔ DTO y elige el código de estado. Cero lógica de negocio.
service/      Casos de uso. Orquesta repositorios y delega las reglas.
validator/    Reglas de negocio complejas, extraídas para no inflar los servicios.
repository/   Spring Data JPA. Consultas derivadas, JPQL con parámetros y Specifications.
domain/       Entidades JPA y conceptos del dominio (calendario laboral, filtros, bloqueo).
dto/          Contratos de entrada y salida. Las entidades JPA nunca se exponen.
exception/    Excepciones de dominio y traducción global a respuestas HTTP.
config/       Reloj, calendario de festivos, carga inicial y OpenAPI.
```

### Por qué así

**Los servicios reciben y devuelven DTOs, no entidades.** No es preferencia de estilo:
`spring.jpa.open-in-view` está desactivado, así que la sesión JPA se cierra al terminar el
método transaccional. Si el mapeo a DTO ocurriera en el controller, cualquier acceso a una
asociación perezosa reventaría con `LazyInitializationException`. Manteniendo la conversión
dentro de la transacción, el controller recibe datos ya materializados y su única
responsabilidad es traducir HTTP.

**Las excepciones de dominio no conocen HTTP.** No llevan `HttpStatus` ni
`@ResponseStatus`. Una excepción de dominio describe qué regla se incumplió; a qué código de
respuesta se traduce eso es una decisión de la capa web y vive únicamente en
`ManejadorGlobalDeErrores`. Esa separación es lo que permitiría exponer los mismos servicios
por otro transporte sin arrastrar semántica HTTP hasta el núcleo.

**Las reglas complejas viven fuera de los servicios.** `ValidadorDeAgendamiento` reúne las
reglas que debe cumplir una cita y `PoliticaDePenalizaciones` reúne la RN-05 entera. Los
servicios quedan orquestando: resuelven referencias, delegan y persisten.

### Identificadores: `Long` autogenerado, no UUID

El enunciado deja la elección abierta ("auto-generado o UUID"). Se eligió `Long` con
`GenerationType.IDENTITY`:

- Las URLs quedan legibles (`/api/citas/7`), lo que importa cuando alguien va a probar la API
  a mano o desde Swagger.
- Un índice sobre `bigint` ocupa la mitad que sobre `uuid` y agrupa mejor, y en este modelo
  todas las consultas críticas van por índice.

UUID sería preferible si los identificadores se expusieran a terceros —un id secuencial
revela cuántos pacientes hay y permite enumerarlos— o si hubiera generación distribuida sin
coordinación. Con autenticación por delante, esa sería la razón para cambiarlo.

### Convención de nombres

El dominio va en **español**, porque son los términos del enunciado y del negocio: `Cita`,
`Medico`, `ValidadorDeAgendamiento`, `PoliticaDePenalizaciones`. Los sufijos y los conceptos
puramente técnicos van en **inglés**, que es como los nombra el framework: `CitaRepository`,
`MedicoService`, `CrearCitaRequest`, `OpenApiConfig`, `ClockConfig`.

La regla práctica: si la palabra aparece en el enunciado, va en español; si aparece en la
documentación de Spring, en inglés. Es la convención habitual en proyectos Spring en
castellano y evita el resultado peor, que es traducir a medias (`RelojConfiguracion`).

### Patrones aplicados

| Patrón | Dónde | Por qué |
|---|---|---|
| **Specification** | `EspecificacionesDeCita` | Cinco filtros opcionales dan 32 combinaciones. Con métodos derivados haría falta una firma por cada una; concatenando JPQL se abriría la puerta a una inyección. |
| **Builder** | Entidades, vía Lombok `@SuperBuilder` | Construcción legible de entidades con muchos campos, sin constructores telescópicos. |
| **Factory method** | `MedicoResponse.desde(...)`, `CitaResponse.desde(...)` | Conversión comprobada por el compilador, sin reflexión ni una capa de mapeo adicional. La dependencia apunta hacia dentro: la API conoce el dominio, el dominio no conoce la API. |
| **Strategy** | `ProveedorDeFestivos` | Punto de extensión del calendario. Declarar otra implementación sustituye el stub sin tocar el dominio. |
| **Template method** | `ManejadorGlobalDeErrores` sobre `ResponseEntityExceptionHandler` | Sobrescribiendo `handleExceptionInternal` se reemplaza de una vez el cuerpo de **todos** los errores de Spring MVC. |

### Decisiones que conviene conocer

<details>
<summary><b>La unicidad de franja la garantiza el esquema, no solo el servicio</b></summary>

Comprobar la disponibilidad con un `exists` previo al INSERT deja una ventana de carrera:
dos peticiones simultáneas pueden leer "libre" y ambas insertar. La defensa correcta es un
índice único parcial (`UNIQUE (medico_id, fecha_hora) WHERE estado = 'PROGRAMADA'`), pero H2
no soporta índices parciales y JPA no permite declararlos de forma portable.

La solución usa el comportamiento estándar de SQL: una restricción `UNIQUE` solo se viola
cuando **todas** las columnas de la clave son no nulas e iguales. La clave incluye una marca
`franja_activa` que vale `TRUE` mientras la cita está `PROGRAMADA` y `null` en cualquier otro
estado:

```sql
constraint uk_cita_medico_franja   unique (medico_id,   fecha_hora, franja_activa)
constraint uk_cita_paciente_franja unique (paciente_id, fecha_hora, franja_activa)
```

Las citas vigentes compiten entre sí por la franja; las canceladas y atendidas quedan fuera
de la restricción y pueden acumularse sobre el mismo horario. Es la semántica exacta de un
índice parcial y funciona igual en H2 y en PostgreSQL. La marca es estado derivado, se
sincroniza en `@PrePersist`/`@PreUpdate` y no tiene getter ni setter públicos, así que no
puede desincronizarse del estado.

El servicio sigue comprobando la disponibilidad antes de insertar, porque así devuelve un 409
con un mensaje útil; la restricción es la red que cubre la ventana que esa comprobación no
puede cerrar.
</details>

<details>
<summary><b>El reloj se inyecta como bean</b></summary>

Casi todas las reglas dependen del "ahora": la antelación de 2 horas, la ventana móvil de 30
días, el rechazo de citas en el pasado. Si los servicios llamaran a `LocalDateTime.now()`,
esas reglas solo podrían probarse esperando en tiempo real o falsificando filas.

Con un `Clock` inyectado, los tests lo sustituyen y verifican el límite exacto de cada regla
de forma determinista. El test de integración de la RN-05 recorre un ciclo de bloqueo y
desbloqueo de 30 días en milisegundos.
</details>

<details>
<summary><b>Las penalizaciones son una tabla de eventos, no un contador</b></summary>

La regla es una ventana móvil de 30 días, no un acumulado histórico. Un contador obligaría a
un proceso que lo decremente cuando cada penalización expira, sería imposible de auditar
("¿por qué está bloqueado este paciente?") y no permitiría calcular la fecha en que volverá a
poder agendar.

Con eventos, el bloqueo es una consulta sobre `fecha_registro`, y cada penalización guarda su
motivo con la antelación concreta (`"Cancelación con 30 min de antelación."`), de modo que
ante un reclamo se puede explicar exactamente qué pasó.
</details>

<details>
<summary><b>Criterio para elegir entre 400 y 409</b></summary>

Varias reglas podrían defenderse en ambos códigos, así que el criterio es explícito:

- **400** cuando la petición es inválida en sí misma y volvería a fallar siempre, haga lo que
  haga el sistema: un domingo nunca será laborable, las 08:15 nunca serán el inicio de una
  franja. El cliente tiene que cambiar la petición.
- **409** cuando la petición es correcta pero choca con el estado actual: la franja está
  ocupada, el paciente está bloqueado. Si ese estado cambia, la misma petición tendría éxito.
</details>

<details>
<summary><b>Por qué no una cadena de estrategias para las reglas de agendamiento</b></summary>

Se valoró modelar cada regla como un bean `ReglaDeAgendamiento` recorrido con un `List`
inyectado. Se descartó: con un conjunto fijo y conocido de reglas, esa indirección esconde el
orden de evaluación —que sí importa, no tiene sentido consultar la agenda del médico para una
hora que ni siquiera es una franja válida— y obliga a abrir un fichero por regla para
entender qué se valida. Un método público que las enumera en orden se lee de un vistazo.

La abstracción se justificaría si las reglas fueran configurables por sede o especialidad,
que no es el caso.
</details>

---

## 3. Tecnologías

| | |
|---|---|
| **Java 21** | LTS. Se usan `record` para los DTOs, patrones en `switch` y bloques de texto. |
| **Spring Boot 3.5.16** | Última estable de la línea 3.x. |
| **Spring Data JPA / Hibernate 6** | Persistencia. |
| **H2** | Base de datos en memoria. Ver justificación abajo. |
| **Bean Validation** | Validación declarativa de los DTOs de entrada. |
| **Lombok** | Reducción de boilerplate, acotado (ver nota). |
| **springdoc-openapi 2.8.17** | Documentación viva de la API. |
| **JUnit 5 · Mockito · AssertJ · MockMvc** | Pruebas. |
| **JaCoCo** | Informe de cobertura. |

### Por qué H2 es válido para este MVP, y cómo se migraría

H2 en memoria permite que quien evalúe el proyecto lo levante con un solo comando, sin
instalar ni configurar nada, y que cada arranque parta de un estado limpio y reproducible.
Para un MVP cuyo objetivo es demostrar reglas de negocio, eso pesa más que la fidelidad al
motor de producción.

Para migrar a PostgreSQL:

1. Cambiar la dependencia `com.h2database:h2` por `org.postgresql:postgresql`.
2. Ajustar `spring.datasource.*` en un perfil `prod` (URL, usuario, contraseña por variables
   de entorno, nunca en el fichero).
3. Sustituir `ddl-auto: create-drop` por `validate` e introducir **Flyway** o **Liquibase**
   para versionar el esquema. Generar la migración inicial a partir del DDL que Hibernate ya
   produce.
4. Sustituir las dos restricciones únicas con marca nulable por índices únicos parciales
   nativos, que PostgreSQL sí soporta:
   `CREATE UNIQUE INDEX ... ON citas (medico_id, fecha_hora) WHERE estado = 'PROGRAMADA'`.
   El comportamiento es idéntico; el índice parcial es simplemente más directo.

**Ninguna consulta usa SQL específico de H2**, así que el código de aplicación no cambia.

### Nota sobre Lombok

Se usa acotado: `@Getter`, `@Setter`, `@SuperBuilder`, `@RequiredArgsConstructor` y `@Slf4j`.
**Nunca `@Data` en entidades JPA**: genera `equals`/`hashCode` sobre todos los campos y un
`toString` que recorre las relaciones perezosas, lo que produce recursión infinita y
`LazyInitializationException`. La igualdad de las entidades está escrita a mano.

---

## 4. Supuestos y ambigüedades resueltas

El enunciado deja varios puntos abiertos. Estos son los supuestos adoptados, todos
verificados con tests.

### RN-04 · Conflicto de paciente (la ambigüedad principal)

El enunciado es contradictorio: el título de la regla dice "mismo médico" pero el texto entre
paréntesis sugiere que aplica incluso con otro médico.

> **Supuesto adoptado:** un paciente no puede tener dos citas `PROGRAMADA` en la misma franja
> horaria, **independientemente del médico**.

Una persona no puede estar en dos consultas simultáneas. Es la lectura amplia, y se eligió
conscientemente, no por descuido.

### RN-01 · Dónde termina la jornada (no es un supuesto: está especificado)

El texto de la RN-01 —"Lunes a viernes 08:00–18:00"— por sí solo no aclara si las 18:00 son
la hora de inicio de la última cita o la hora de cierre. Pero la sección **Datos de
Referencia** del enunciado lo resuelve sin ambigüedad:

> Franja **20**: 17:30 - 18:00 · En sábado (hasta 13:00): franjas **1-10**

> **Comportamiento implementado:** una cita solo es válida si **termina** dentro de la
> jornada. La última franja entre semana empieza a las **17:30** y la del sábado a las
> **12:30**. Son exactamente 20 franjas entre semana y 10 el sábado.

Se recoge aquí porque es un borde fácil de equivocar por uno, no porque haya habido que
decidirlo. `FlujoCompletoTest.bordesDeLasFranjasDelEnunciado` comprueba los cuatro límites
contra el agendamiento real: 12:30 del sábado se reserva, las 13:00 no; 17:30 entre semana se
reserva, las 18:00 no.

### RN-02 · Qué citas ocupan una franja

La regla dice "un médico no puede tener dos citas en la misma franja", sin distinguir por
estado.

> **Supuesto adoptado:** solo las citas en estado `PROGRAMADA` ocupan franja. Una cita
> cancelada la libera y el horario vuelve a ofrecerse.

De lo contrario cancelar no serviría de nada: el hueco quedaría bloqueado para siempre y la
RF-05 perdería su sentido. Aplica igual a la RN-04.

### RN-05 · El límite de las 2 horas

"Menos de 2 horas" no aclara qué pasa exactamente en las 2 horas.

> **Supuesto adoptado:** cancelar con 2 horas exactas **no** penaliza. El borde se interpreta
> a favor del paciente.

Además, cancelar **después** de la hora de la cita da una antelación negativa, que cae por
debajo del umbral y penaliza. Es el peor caso posible y se trata como tal.

### RN-05 · Cuándo expira exactamente una penalización

> **Supuesto adoptado:** una penalización deja de contar **exactamente** 30 días después de
> registrarse (límite estricto, no "mayor o igual").

Con un límite inclusivo, una penalización de justo 30 días seguiría contando en el instante
en que la API acaba de prometerle al paciente que ya puede agendar: la respuesta se
contradiría a sí misma.

### RN-05 · Desbloqueo con más de tres penalizaciones

El enunciado dice "hasta que la penalización más antigua de esas 3 expire". Con exactamente
tres es correcto, pero **con cinco, tomar la más antigua daría una fecha en la que el paciente
seguiría bloqueado**: al expirar la primera aún le quedarían cuatro.

> **Supuesto adoptado:** el desbloqueo llega cuando expira la **tercera empezando por el
> final**, que es el momento en que quedan menos de tres vigentes.

Con exactamente tres penalizaciones coincide con lo que dice el enunciado; con más, lo
generaliza sin prometer fechas falsas.

### RN-06 · ¿Puede reprogramar un paciente bloqueado?

El enunciado enumera para la reprogramación la RN-02 y la RN-04, **no la RN-05**.

> **Supuesto adoptado:** un paciente bloqueado **sí** puede reprogramar una cita que ya tenía;
> lo que no puede es agendar citas nuevas.

Un paciente bloqueado que no pudiera mover una cita existente solo tendría dos salidas: no
presentarse, o cancelarla — y cancelar le sumaría *otra* penalización. Bloquear la
reprogramación empujaría justo a la conducta que la regla quiere evitar. Reprogramar tarde
**sí registra penalización**; lo que no hace es impedir la operación que la genera.

### RN-06 · Reprogramar al mismo horario

> **Supuesto adoptado:** se rechaza con 400 (`REPROGRAMACION_SIN_CAMBIO`).

Sin esta comprobación explícita, la cita chocaría consigo misma durante la validación y el
mensaje diría que el médico ya tiene una cita en esa franja, siendo esa cita la suya.

### Agendar en el pasado

No está en el enunciado.

> **Supuesto adoptado:** no se permite agendar citas en el pasado (400,
> `FECHA_EN_EL_PASADO`).

No describe ninguna operación real y dejaría sin sentido la RN-05, que mide la antelación de
la cancelación respecto a la cita.

### RN-03 · Edad mínima

> **Supuesto adoptado:** la fecha de nacimiento es opcional; su ausencia equivale a edad 0,
> que es válida y no impide agendar. No se implementa edad mínima mayor que 0 ni edad máxima,
> porque el enunciado no las pide. Una fecha de nacimiento futura se rechaza con 400.

### Festivos

El enunciado no aporta lista de festivos.

> **Supuesto adoptado:** `ProveedorDeFestivos` (el `HolidayProvider` del enunciado) es una
> interfaz con una implementación por defecto que no considera festivo ningún día.

Se registra con `@ConditionalOnMissingBean`, de modo que declarar otra implementación —una que
consulte una tabla o un servicio externo— la sustituye sin tocar el dominio. En producción se
integraría con un calendario real; los festivos colombianos siguen la Ley Emiliani y se
desplazan al lunes siguiente, así que no son constantes calculables de forma trivial.

---

## 5. Endpoints

Todos devuelven y aceptan JSON. La documentación interactiva está en
[Swagger UI](http://localhost:8080/swagger-ui.html) con ejemplos precargados en cada
petición.

### Colección de Postman

En [`postman/`](postman/) hay una colección lista para importar que recorre la API entera:
**39 peticiones en 7 carpetas numeradas**, encadenando los identificadores automáticamente
para no tener que copiar ids a mano.

No es la importación cruda del OpenAPI: está ordenada para contar la historia del dominio.
Después del camino feliz, provoca a propósito el rechazo de cada regla —franja ocupada por el
médico (RN-02) y por el paciente (RN-04), domingo y horas fuera de jornada (RN-01), franja
desalineada, fecha pasada, documento duplicado— y termina comprobando que hasta los errores
de Spring MVC salen con el cuerpo común.

**Ninguna fecha está fija.** Un script de la colección calcula en cada ejecución el próximo
día con atención, el próximo domingo y el día de ayer, así que no caduca ni hay que editarla.
Los documentos de identidad también se generan por ejecución, para poder repetirla sin
reiniciar la aplicación.

Cada petición lleva sus propias aserciones, así que la colección se puede ejecutar entera
—desde el Collection Runner o por consola— y sirve como prueba de humo del despliegue:

```bash
npx newman run postman/MediSalud.postman_collection.json
# 39 peticiones · 80 aserciones · 0 fallos
```

Si arrancas con Docker en otro puerto, cambia la variable `baseUrl` de la colección.

| Método | Ruta | Descripción | Éxito |
|---|---|---|---|
| `POST` | `/api/medicos` | Registrar médico | 201 + `Location` |
| `GET` | `/api/medicos` | Listar médicos | 200 |
| `GET` | `/api/medicos/{id}` | Obtener médico | 200 |
| `GET` | `/api/medicos/{medicoId}/disponibilidad` | Franjas libres en un rango | 200 |
| `POST` | `/api/pacientes` | Registrar paciente | 201 + `Location` |
| `GET` | `/api/pacientes` | Listar pacientes | 200 |
| `GET` | `/api/pacientes/{id}` | Obtener paciente | 200 |
| `POST` | `/api/citas` | Reservar cita | 201 + `Location` |
| `GET` | `/api/citas` | Listar citas con filtros | 200 |
| `GET` | `/api/citas/{id}` | Obtener cita | 200 |
| `PATCH` | `/api/citas/{id}/cancelar` | Cancelar cita | 200 |
| `PATCH` | `/api/citas/{id}/reprogramar` | Reprogramar cita | 200 |

### Códigos de estado

| Código | Cuándo |
|---|---|
| `200` | Consulta o actualización correcta |
| `201` | Recurso creado. Incluye cabecera `Location` |
| `400` | Entrada inválida, o regla que fallaría siempre (fuera de horario, franja desalineada, fecha pasada) |
| `404` | El recurso referenciado no existe |
| `409` | Conflicto con el estado actual (franja ocupada, documento duplicado, paciente bloqueado) |
| `500` | Error no controlado. Mensaje genérico, sin detalles internos |

### Formato de error

**Todas** las respuestas de error comparten el mismo cuerpo, incluidas las que genera Spring
MVC (405, 415, JSON ilegible, parámetro mal tipado):

```json
{
  "codigo": "MEDICO_NO_DISPONIBLE",
  "mensaje": "El médico ya tiene una cita programada en esa franja.",
  "timestamp": "2026-08-03T09:15:22.481-05:00",
  "ruta": "/api/citas"
}
```

`detalles` aparece solo en errores de validación:

```json
{
  "codigo": "VALIDACION_FALLIDA",
  "mensaje": "La petición contiene campos inválidos.",
  "timestamp": "2026-08-03T09:15:22.481-05:00",
  "ruta": "/api/medicos",
  "detalles": [
    { "campo": "email", "mensaje": "El email debe tener un formato válido." },
    { "campo": "nombreCompleto", "mensaje": "El nombre completo debe tener entre 3 y 100 caracteres." }
  ]
}
```

El campo `codigo` es un valor estable pensado para programar contra él; el `mensaje` está
pensado para leerse. Reformular un texto nunca rompe a un cliente.

### Ejemplos

> Las fechas de los ejemplos deben ser **futuras** y caer en horario de atención. Ajústalas si
> ya pasaron.

<details open>
<summary><b>Registrar un paciente</b></summary>

```bash
curl -i -X POST http://localhost:8080/api/pacientes \
  -H "Content-Type: application/json" \
  -d '{
        "nombreCompleto": "Juan Pérez",
        "documentoIdentidad": "1020304050",
        "telefono": "3001234567",
        "email": "juan.perez@example.com",
        "fechaNacimiento": "1990-05-20"
      }'
```

```
HTTP/1.1 201 Created
Location: http://localhost:8080/api/pacientes/1
```
```json
{
  "id": 1,
  "nombreCompleto": "Juan Pérez",
  "documentoIdentidad": "1020304050",
  "telefono": "3001234567",
  "email": "juan.perez@example.com",
  "fechaNacimiento": "1990-05-20"
}
```
</details>

<details>
<summary><b>Consultar disponibilidad</b></summary>

```bash
curl "http://localhost:8080/api/medicos/1/disponibilidad?fechaInicio=2026-08-03&fechaFin=2026-08-04"
```

```json
{
  "medico": { "id": 1, "nombreCompleto": "Dra. María González", "especialidad": "Cardiología" },
  "fechaInicio": "2026-08-03",
  "fechaFin": "2026-08-04",
  "totalFranjasDisponibles": 39,
  "dias": [
    { "fecha": "2026-08-03", "franjas": ["08:00:00", "08:30:00", "09:00:00", "…", "17:30:00"] },
    { "fecha": "2026-08-04", "franjas": ["08:00:00", "…", "17:30:00"] }
  ]
}
```

Los domingos y festivos se omiten del array. Un día laborable sin huecos **sí** aparece, con
`"franjas": []`: "está todo reservado" es información distinta de "ese día no atendemos".

Para reservar, la `fechaHora` se compone como `fecha + "T" + franja` →
`2026-08-03T09:00:00`.
</details>

<details>
<summary><b>Reservar una cita</b></summary>

```bash
curl -i -X POST http://localhost:8080/api/citas \
  -H "Content-Type: application/json" \
  -d '{ "medicoId": 1, "pacienteId": 1, "fechaHora": "2026-08-03T09:00:00" }'
```

```
HTTP/1.1 201 Created
Location: http://localhost:8080/api/citas/1
```
```json
{
  "id": 1,
  "medico": { "id": 1, "nombreCompleto": "Dra. María González", "especialidad": "Cardiología" },
  "paciente": { "id": 1, "nombreCompleto": "Juan Pérez", "documentoIdentidad": "1020304050" },
  "fechaHora": "2026-08-03T09:00:00",
  "estado": "PROGRAMADA"
}
```

Repetir la misma franja con el mismo médico devuelve **409 `MEDICO_NO_DISPONIBLE`**; con el
mismo paciente y otro médico, **409 `PACIENTE_NO_DISPONIBLE`**.
</details>

<details>
<summary><b>Listar citas con filtros</b></summary>

Todos los filtros son opcionales y se combinan entre sí. El rango cubre días completos, ambos
extremos incluidos.

```bash
curl "http://localhost:8080/api/citas?medicoId=1&estado=PROGRAMADA&fechaInicio=2026-08-01&fechaFin=2026-08-31"
```

| Parámetro | Tipo | Ejemplo |
|---|---|---|
| `medicoId` | entero | `1` |
| `pacienteId` | entero | `1` |
| `estado` | enum | `PROGRAMADA`, `CANCELADA`, `ATENDIDA` |
| `fechaInicio` | fecha ISO | `2026-08-01` |
| `fechaFin` | fecha ISO | `2026-08-31` |
</details>

<details>
<summary><b>Cancelar una cita</b></summary>

```bash
curl -X PATCH http://localhost:8080/api/citas/1/cancelar
```

```json
{
  "cita": { "id": 1, "estado": "CANCELADA", "fechaCancelacion": "2026-08-03T08:15:00", "…": "…" },
  "penalizacion": {
    "registrada": true,
    "motivo": "Cancelación con 45 min de antelación.",
    "totalVigentes": 3,
    "pacienteBloqueado": true,
    "puedeAgendarDesde": "2026-09-02T09:30:00"
  }
}
```

La respuesta informa de las consecuencias en el momento: si la cancelación costó una
penalización, cuántas acumula y si acaba de quedarse sin poder agendar. Sin eso el paciente
cancela sin enterarse y lo descubre al intentar reservar, cuando ya no puede hacer nada.

Cancelando con 2 horas o más de antelación, `registrada` es `false` y los campos que no
aplican desaparecen del JSON.
</details>

<details>
<summary><b>Reprogramar una cita</b></summary>

```bash
curl -X PATCH http://localhost:8080/api/citas/1/reprogramar \
  -H "Content-Type: application/json" \
  -d '{ "nuevaFechaHora": "2026-08-03T11:00:00" }'
```

```json
{
  "citaAnterior": { "id": 1, "estado": "CANCELADA", "fechaHora": "2026-08-03T09:00:00", "…": "…" },
  "citaNueva":    { "id": 2, "estado": "PROGRAMADA", "fechaHora": "2026-08-03T11:00:00", "citaOrigenId": 1, "…": "…" },
  "penalizacion": { "registrada": false, "totalVigentes": 0, "pacienteBloqueado": false }
}
```

Devuelve las **dos** citas porque reprogramar no modifica una cita: cierra una y abre otra,
enlazadas por `citaOrigenId`. Si el nuevo horario no está libre, la operación completa se
deshace y el paciente conserva su cita original intacta.
</details>

<details>
<summary><b>Un error de validación</b></summary>

```bash
curl -X POST http://localhost:8080/api/medicos \
  -H "Content-Type: application/json" \
  -d '{ "nombreCompleto": "Ab", "especialidad": "", "telefono": "123", "email": "malo" }'
```

```
HTTP/1.1 400 Bad Request
```
```json
{
  "codigo": "VALIDACION_FALLIDA",
  "mensaje": "La petición contiene campos inválidos.",
  "timestamp": "2026-08-03T09:15:22.481-05:00",
  "ruta": "/api/medicos",
  "detalles": [
    { "campo": "email", "mensaje": "El email debe tener un formato válido." },
    { "campo": "especialidad", "mensaje": "La especialidad es obligatoria." },
    { "campo": "nombreCompleto", "mensaje": "El nombre completo debe tener entre 3 y 100 caracteres." },
    { "campo": "telefono", "mensaje": "El teléfono debe contener al menos 7 dígitos y solo admite dígitos, espacios y los símbolos + - ( ) ." }
  ]
}
```
</details>

---

## 6. Tests

```bash
./mvnw test          # o mvn test
```

**198 tests en 21 clases.** Cobertura: **98,3 % de líneas**, **90,2 % de ramas**.

El informe de JaCoCo se genera con el mismo comando en
`target/site/jacoco/index.html`.

| Nivel | Clases | Qué cubre |
|---|---|---|
| **Unitario sin Spring** | `ValidacionDeSolicitudesTest`, `CalendarioLaboralTest`, `JornadaLaboralTest`, `BaseEntityTest` | Validaciones de los DTOs, horario laboral y generación de franjas, invariantes del dominio, contrato de igualdad de las entidades. |
| **Unitario con Mockito** | `ValidadorDeAgendamientoTest`, `PoliticaDePenalizacionesTest`, `CitaServiceTest`, `PacienteServiceTest`, `DisponibilidadServiceTest`, `CargaInicialMedicosTest` | Cada regla de negocio en sus casos límite, con reloj fijo. Y los caminos que la integración no puede alcanzar: las colisiones de integridad concurrentes. |
| **Rodaja JPA** | `ModeloPersistenciaTest` | Mapeo del grafo completo y las restricciones únicas contra H2 real. |
| **Rodaja web** | `MedicoControllerTest`, `PacienteControllerTest`, `CitaControllerTest`, `DisponibilidadControllerTest` | Contrato HTTP: códigos de estado, forma de las respuestas y del cuerpo de error. |
| **Integración end-to-end** | `FlujoCompletoTest`, `CancelacionYPenalizacionTest`, `ReprogramacionTest`, `ListadoDeCitasTest`, `DocumentacionOpenApiTest` | Contexto completo, base de datos y peticiones HTTP reales. |
| **Configuración de despliegue** | `ConfiguracionDeDespliegueTest` | Que la imagen no arranque con el perfil de desarrollo y deje abierta la consola H2. |

### Algunos tests que merece la pena mirar

- **`FlujoCompletoTest`** recorre el camino de una recepción real: registrar → documento
  duplicado (409) → consultar disponibilidad → agendar → chocar con RN-02 → chocar con RN-04
  → cancelar tarde → verificar la penalización. Es el que comprueba que las piezas encajan
  entre sí, no solo que cada una funciona.

- **`CancelacionYPenalizacionTest`** recorre el ciclo entero de la RN-05 con un reloj
  manipulable: tres cancelaciones tardías, bloqueo con la fecha exacta, 409 un minuto antes
  de que expire la ventana y reserva correcta un minuto después.

- **`ListadoDeCitasTest`** cuenta las sentencias que Hibernate prepara realmente y exige que
  el listado resuelva en **una sola consulta**. Sin el `@EntityGraph` serían nueve para cuatro
  citas.

- **`MedicoControllerTest`** provoca un fallo con un mensaje cargado de datos internos (URL de
  JDBC, usuario, nombres de paquete) y afirma que **ninguno** aparece en el cuerpo de la
  respuesta.

- **`BaseEntityTest`** cubre el caso que motivó el `hashCode` constante: una entidad metida en
  un `HashSet` antes de persistirse debe seguir encontrándose después de que JPA le asigne el
  identificador.

- **`FlujoCompletoTest.bordesDeLasFranjasDelEnunciado`** comprueba contra el agendamiento real
  los cuatro límites de la tabla de *Datos de Referencia*: el sábado se reserva a las 12:30
  pero no a las 13:00, y entre semana a las 17:30 pero no a las 18:00.

No se impone un umbral de cobertura que rompa la construcción: un mínimo que nadie ajusta
acaba invitando a escribir tests que solo suman líneas. El informe está para leerlo.

---

## 7. Docker y despliegue

### Construir y ejecutar

```bash
docker build -t medisalud/agendamiento-citas:1.0.0 .
docker run --rm -p 8080:8080 medisalud/agendamiento-citas:1.0.0
```

Verificado: la imagen construye en unos 55 s, pesa **397 MB**, arranca en **5 s** y precarga
los tres médicos.

El `Dockerfile` es multietapa:

- **Construcción** con `maven:3.9-eclipse-temurin-21`. El `pom.xml` se copia antes que el
  código y las dependencias se descargan en una capa aparte, así que mientras el pom no cambie
  editar código reutiliza esa capa desde la caché.
- **Ejecución** con `eclipse-temurin:21-jre-alpine`. Solo el JRE y el jar: sin Maven, sin JDK
  y sin código fuente, lo que reduce el tamaño y la superficie expuesta.
- La aplicación corre con un **usuario sin privilegios**, no como root.
- El propietario del jar se fija en el propio `COPY --chown`, no con un `RUN chown` posterior.
  Las capas son copy-on-write: un `chown` sobre el jar reescribe sus 56 MB enteros en una
  capa nueva y deja el artefacto almacenado dos veces. Corregirlo bajó la imagen de 508 MB a
  397 MB.
- `-XX:MaxRAMPercentage=75.0` en lugar de un `-Xmx` fijo, para que la JVM se ajuste al límite
  de memoria que le imponga el orquestador sin reconstruir la imagen.
- Arranca con el perfil **`docker`**, no `dev`: sin consola H2 y sin volcado de SQL a los
  logs (ver la nota de la sección 1).

### Despliegue en la nube

El artefacto es un jar autocontenido, así que sirve cualquier plataforma que ejecute
contenedores. Con la imagen ya construida:

```bash
# Google Cloud Run
gcloud run deploy medisalud-citas \
  --image gcr.io/PROYECTO/medisalud-citas:1.0.0 \
  --region us-central1 --port 8080 --allow-unauthenticated

# AWS App Runner, Azure Container Apps, Render, Railway, Fly.io: equivalente
```

**Antes de un despliegue real** habría que resolver, como mínimo:

1. **Base de datos gestionada** (PostgreSQL) con las credenciales en variables de entorno o en
   un gestor de secretos, nunca en el repositorio, y un perfil `prod` con
   `ddl-auto: validate` más migraciones versionadas.
2. **Autenticación y autorización.** El enunciado no las pide y la API está completamente
   abierta: hoy cualquiera puede listar los datos de todos los pacientes.
3. **Actuator** para *liveness* y *readiness*, que es lo que necesita el orquestador para
   saber si el contenedor está sano. Con eso se podría añadir un `HEALTHCHECK` al Dockerfile.
4. **HTTPS** terminado en el balanceador, y CORS configurado para los orígenes del frontend.
5. **Límite de peticiones** por IP, especialmente en los endpoints de consulta.

---

## 8. Limitaciones conocidas

Cosas que faltan a propósito, con la razón:

| Limitación | Por qué y qué haría falta |
|---|---|
| **Sin autenticación** | El enunciado no la pide. En producción es lo primero: hoy la API está abierta y expone datos personales de pacientes. |
| **Listado de citas sin paginar** | El enunciado define la respuesta como una lista. El repositorio ya extiende `JpaSpecificationExecutor`, así que sería cambiar la firma por una que acepte `Pageable` sin tocar los filtros. |
| **Base de datos en memoria** | Elegido para que arranque con un comando. Ver la ruta de migración en la sección 3. |
| **Sin calendario de festivos real** | Punto de extensión preparado (`ProveedorDeFestivos`), implementación por defecto vacía. |
| **Sin metamodelo estático de JPA** | Los nombres de atributo en las `Specification` van en constantes, no comprobados en compilación. Se descartó `hibernate-jpamodelgen` para no sumar otro procesador de anotaciones junto a Lombok y mantener la construcción sin sorpresas. |
| **Sin estado `ATENDIDA` alcanzable** | El enum lo contempla y el listado permite filtrar por él, pero no hay endpoint que marque una cita como atendida porque el enunciado no lo pide. |
