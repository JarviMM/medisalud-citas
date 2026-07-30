package com.medisalud.agenda.domain;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

/**
 * Raiz comun de las entidades JPA del dominio.
 *
 * <p>Centraliza el identificador tecnico y la semantica de igualdad para evitar repetir
 * el mismo bloque en cada entidad (DRY).</p>
 *
 * <p><b>Sobre {@code equals}/{@code hashCode}:</b> se comparan por identificador y nunca
 * por el resto de atributos. Dos entidades solo son iguales si ambas estan persistidas y
 * comparten id; una entidad transitoria (id {@code null}) solo es igual a si misma. El
 * {@code hashCode} es constante por tipo para que una entidad no cambie de bucket dentro
 * de un {@code HashSet} cuando pasa de transitoria a persistida. Se usa
 * {@link Hibernate#getClass(Object)} en lugar de {@code getClass()} porque un proxy
 * perezoso es una subclase generada y la comparacion directa fallaria.</p>
 *
 * <p>Se declaran {@code final} deliberadamente: ninguna entidad debe redefinir la
 * identidad, ese es justamente el error que este contrato evita.</p>
 */
@MappedSuperclass
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || Hibernate.getClass(this) != Hibernate.getClass(obj)) {
            return false;
        }
        BaseEntity otra = (BaseEntity) obj;
        return id != null && id.equals(otra.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
