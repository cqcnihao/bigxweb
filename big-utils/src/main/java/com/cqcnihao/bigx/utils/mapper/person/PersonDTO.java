package com.cqcnihao.bigx.utils.mapper.person;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Person 数据传输对象
 * 
 * @deprecated 此类与 Person 完全相同，建议使用 MapStruct 直接映射或使用 Person 类
 *             保留此类仅为兼容现有代码，后续版本将移除
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class PersonDTO extends Person {
    // 此类与 Person 完全相同，暂时保留以兼容现有代码
    // 建议在后续版本中直接使用 Person 类或通过 MapStruct 转换
}
