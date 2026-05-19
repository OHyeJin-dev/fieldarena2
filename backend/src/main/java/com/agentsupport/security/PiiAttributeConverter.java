package com.agentsupport.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PiiAttributeConverter implements AttributeConverter<String, String> {

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) return null;
    PiiEncryptor enc = PiiEncryptor.instance();
    if (enc == null) return attribute;
    return enc.encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) return null;
    PiiEncryptor enc = PiiEncryptor.instance();
    if (enc == null) return dbData;
    try {
      return enc.decrypt(dbData);
    } catch (Exception e) {
      // fallback for legacy plain-text seed data
      return dbData;
    }
  }
}
