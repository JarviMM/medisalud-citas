# ---------------------------------------------------------------------------
# Etapa 1: construcción
#
# Se copia primero el pom y se descargan las dependencias en una capa aparte.
# Así, mientras el pom no cambie, editar código reutiliza esa capa desde la
# caché en lugar de volver a bajar medio Maven Central en cada build.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS construccion

WORKDIR /proyecto

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
# Los tests se ejecutan en el pipeline, no aquí: repetirlos alargaría cada
# build de imagen sin aportar información nueva, y ligarían la publicación de
# la imagen a que haya red disponible para las dependencias de test.
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------
# Etapa 2: ejecución
#
# Solo el JRE y el jar. La imagen final no lleva Maven, ni el JDK, ni el código
# fuente, así que pesa una fracción de la de construcción y expone mucha menos
# superficie.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS ejecucion

# Usuario sin privilegios: si alguien logra ejecutar código dentro del
# contenedor, no lo hace como root.
RUN addgroup -S medisalud && adduser -S -G medisalud medisalud

# Directorio donde vive la base cuando el despliegue monta un volumen. Se crea
# aquí, en la imagen, y no solo en el compose: un volumen nombrado hereda el
# propietario del directorio que encuentra en la imagen al crearse. Si no
# existiera, Docker lo crearía como root y la aplicación, que corre sin
# privilegios, no podría escribir en él.
RUN mkdir -p /datos && chown medisalud:medisalud /datos

WORKDIR /aplicacion

# El propietario se fija en el propio COPY, no con un RUN chown posterior. Las
# capas son copy-on-write: un chown sobre el jar reescribiría sus 56 MB enteros
# en una capa nueva, dejando el artefacto almacenado dos veces en la imagen.
COPY --from=construccion --chown=medisalud:medisalud /proyecto/target/*.jar aplicacion.jar

USER medisalud

EXPOSE 8080

# MaxRAMPercentage en lugar de un -Xmx fijo: la JVM se ajusta al límite de
# memoria que le imponga el orquestador sin tener que reconstruir la imagen.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Perfil `docker`, nunca `dev`. El perfil dev deja abierta la consola H2 en
# /h2-console, que ejecuta SQL arbitrario sin credenciales: cómodo en local,
# una puerta abierta a la base de datos en un contenedor publicado.
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /aplicacion/aplicacion.jar"]
