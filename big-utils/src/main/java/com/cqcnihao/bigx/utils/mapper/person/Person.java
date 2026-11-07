package com.cqcnihao.bigx.utils.mapper.person;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class Person {


    private Long id;

    private String pNo;

    private String pName;

    private Date createTime;

    private String createUser;

    private String remarks;

    private List<String> loves;

}
