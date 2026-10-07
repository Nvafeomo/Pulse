package com.pulse.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA converter that stores a list of {@link Channel} values in a single
 * column as a comma-separated string of enum names (e.g. "EMAIL,SMS").
 * An empty or null list is stored as an empty string and read back as an
 * empty list.
 */
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
