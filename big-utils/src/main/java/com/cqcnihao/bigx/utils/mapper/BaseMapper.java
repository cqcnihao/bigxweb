package com.cqcnihao.bigx.common.mapper;

public interface BaseMapper<E, D> {

    D toDto(E entity);

    E toEntity(D dto);
}
