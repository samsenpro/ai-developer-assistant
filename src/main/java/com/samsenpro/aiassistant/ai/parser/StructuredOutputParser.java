package com.samsenpro.aiassistant.ai.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Capa de validación de las respuestas del modelo:
 * <pre>
 * respuesta en bruto → extracción del JSON → parseo → campos inesperados → DTO → Bean Validation
 * </pre>
 * No se asume que el LLM devuelva JSON válido: cualquier desviación termina en
 * {@link InvalidAIResponseException} y nunca en datos corruptos para el cliente.
 */
@Component
public class StructuredOutputParser {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputParser.class);
    private static final int MAX_NESTING = 5;

    private final ObjectMapper mapper;
    private final Validator validator;

    public StructuredOutputParser(Validator validator) {
        this.validator = validator;
        this.mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                // Los campos inesperados se detectan y se informan aparte (ver findUnexpectedFields)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
    }

    /**
     * @param raw       texto devuelto por el modelo
     * @param truncated el proveedor indicó que cortó la respuesta por límite de tokens
     */
    public <T> ParsedOutput<T> parse(String raw, Class<T> type, boolean truncated) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAIResponseException(InvalidAIResponseException.Reason.EMPTY_RESPONSE,
                    "The AI provider returned an empty response", List.of());
        }

        JsonNode tree;
        try {
            tree = mapper.readTree(extractJson(raw));
        } catch (JsonProcessingException ex) {
            if (truncated) {
                throw new InvalidAIResponseException(InvalidAIResponseException.Reason.TRUNCATED_RESPONSE,
                        "The AI response was cut off before it was complete", List.of());
            }
            log.warn("AI response is not valid JSON ({} chars)", raw.length());
            throw new InvalidAIResponseException(InvalidAIResponseException.Reason.INVALID_JSON,
                    "The AI response is not valid JSON", List.of());
        }
        if (tree == null || !tree.isObject()) {
            throw new InvalidAIResponseException(InvalidAIResponseException.Reason.INVALID_JSON,
                    "The AI response is not a JSON object", List.of());
        }

        List<String> unexpected = new ArrayList<>();
        findUnexpectedFields(tree, mapper.constructType(type), "", unexpected, 0);

        T value;
        try {
            value = mapper.treeToValue(tree, type);
        } catch (JsonProcessingException ex) {
            String path = ex instanceof JsonMappingException mapping ? describePath(mapping) : "";
            throw new InvalidAIResponseException(InvalidAIResponseException.Reason.SCHEMA_VIOLATION,
                    "The AI response does not match the expected structure",
                    List.of((path.isEmpty() ? "response" : path) + ": invalid value"));
        }
        if (value == null) {
            throw new InvalidAIResponseException(InvalidAIResponseException.Reason.EMPTY_RESPONSE,
                    "The AI provider returned an empty response", List.of());
        }

        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            List<String> messages = violations.stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .toList();
            throw new InvalidAIResponseException(InvalidAIResponseException.Reason.SCHEMA_VIOLATION,
                    "The AI response is missing required fields or has invalid values", messages);
        }
        if (!unexpected.isEmpty()) {
            log.info("AI response contained {} unexpected field(s) that were discarded: {}", unexpected.size(),
                    unexpected);
        }
        return new ParsedOutput<>(value, List.copyOf(unexpected));
    }

    /**
     * Los modelos suelen envolver el JSON en bloques ```json ... ``` o añadir una frase antes. Se
     * toma el objeto JSON más externo; si el texto sigue sin ser JSON, el parseo fallará.
     */
    static String extractJson(String raw) {
        String text = raw.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int closingFence = text.lastIndexOf("```");
            if (firstNewline > 0 && closingFence > firstNewline) {
                text = text.substring(firstNewline + 1, closingFence).strip();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    /** Recorre el JSON y el tipo destino a la vez y anota los campos que el tipo no declara. */
    private void findUnexpectedFields(JsonNode node, JavaType type, String path, List<String> unexpected, int depth) {
        if (depth > MAX_NESTING || node == null) {
            return;
        }
        if (type.isCollectionLikeType() && node.isArray()) {
            JavaType elementType = type.getContentType();
            for (int i = 0; i < node.size(); i++) {
                findUnexpectedFields(node.get(i), elementType, path + "[" + i + "]", unexpected, depth + 1);
            }
            return;
        }
        if (!node.isObject() || !isBean(type)) {
            return;
        }
        BeanDescription description = mapper.getDeserializationConfig().introspect(type);
        Map<String, JavaType> properties = description.findProperties().stream()
                .collect(Collectors.toMap(BeanPropertyDefinition::getName, BeanPropertyDefinition::getPrimaryType,
                        (a, b) -> a));
        Iterator<Map.Entry<String, JsonNode>> fields = ((ObjectNode) node).fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
            JavaType propertyType = properties.get(field.getKey());
            if (propertyType == null) {
                unexpected.add(fieldPath);
            } else {
                findUnexpectedFields(field.getValue(), propertyType, fieldPath, unexpected, depth + 1);
            }
        }
    }

    private static boolean isBean(JavaType type) {
        Class<?> raw = type.getRawClass();
        return !raw.isPrimitive() && !raw.isEnum() && !raw.getName().startsWith("java.")
                && !type.isMapLikeType() && !type.isContainerType();
    }

    private static String describePath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : ex.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
