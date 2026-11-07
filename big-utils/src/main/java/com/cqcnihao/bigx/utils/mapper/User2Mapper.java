package com.cqcnihao.bigx.utils.mapper;

import com.cqcnihao.bigx.utils.mapper.user.User;
import com.cqcnihao.bigx.utils.mapper.user.UserDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface User2Mapper {

    UserDTO toDto(User user);

    User toEntity(UserDTO dto);
}
