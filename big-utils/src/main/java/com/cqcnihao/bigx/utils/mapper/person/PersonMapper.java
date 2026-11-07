package com.cqcnihao.bigx.utils.mapper.person;

import com.cqcnihao.bigx.common.mapper.BaseMapper;
import com.cqcnihao.bigx.utils.mapper.user.User;
import com.cqcnihao.bigx.utils.mapper.user.UserDTO;
import lombok.Data;
import org.mapstruct.Mapper;

import java.util.Date;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PersonMapper extends BaseMapper<Person, PersonDTO> {

}
