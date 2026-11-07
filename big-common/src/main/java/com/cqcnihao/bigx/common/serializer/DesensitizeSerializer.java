package com.cqcnihao.bigx.common.serializer;

import com.cqcnihao.bigx.common.annotation.Desensitize;
import com.cqcnihao.bigx.common.enums.DesensitizeType;
import com.cqcnihao.bigx.common.util.DesensitizeUtil;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

@AllArgsConstructor
@NoArgsConstructor
public class DesensitizeSerializer extends ValueSerializer<String> {

    private DesensitizeType type;

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
            return;
        }

        // 响应加密路径：密文本身已保护数据，跳过脱敏，避免解密后仍拿不到完整明文
        if (DesensitizeContext.isBypass()) {
            gen.writeString(value);
            return;
        }

        String desensitizedValue = switch (type) {
            case MOBILE -> DesensitizeUtil.desensitizeMobile(value);
            case EMAIL -> DesensitizeUtil.desensitizeEmail(value);
            case ID_CARD -> DesensitizeUtil.desensitizeIdCard(value);
            case BANK_CARD -> DesensitizeUtil.desensitizeBankCard(value);
            case NAME -> DesensitizeUtil.desensitizeName(value);
            case ADDRESS -> DesensitizeUtil.desensitizeAddress(value);
            default -> DesensitizeUtil.desensitizeDefault(value);
        };

        gen.writeString(desensitizedValue);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext serializers, BeanProperty property) {
        if (property != null) {
            Desensitize annotation = property.getAnnotation(Desensitize.class);
            if (annotation != null) {
                return new DesensitizeSerializer(annotation.type());
            }
        }
        return this;
    }
}