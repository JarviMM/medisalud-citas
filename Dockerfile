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

WORKDIR /aplicacion
COPY --from=construccion /proyecto/target/*.jar aplicacion.jar
RUN chown medisalud:medisalud aplicacion.jar

USER medisalud

EXPOSE 8080

# MaxRAMPercentage en lugar de un -Xmx fijo: la JVM se ajusta al límite de
# memoria que le imponga el orquestador sin tener que reconstruir la imagen.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=dev

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /aplicacion/aplicacion.jar"]
