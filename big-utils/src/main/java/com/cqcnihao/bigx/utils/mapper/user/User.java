package com.cqcnihao.bigx.utils.mapper.user;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
//@Entity
@Data
@TableName("user")
public class User {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private LocalDateTime createdAt;
    // getters, setters...

}
