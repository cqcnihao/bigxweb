package com.cqcnihao.bigx.utils.mapper.user;

import com.cqcnihao.bigx.common.mapper.BaseMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<User, UserDTO> {

}
