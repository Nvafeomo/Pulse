package com.pulse.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class ChannelListConverter implements AttributeConverter<List<Channel>, String> {

    @Override
    public String convertToDatabaseColumn(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) return "";
        return channels.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public List<Channel> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        return Arrays.stream(dbData.split(","))
                .map(Channel::valueOf)
                .collect(Collectors.toList());
    }
}
