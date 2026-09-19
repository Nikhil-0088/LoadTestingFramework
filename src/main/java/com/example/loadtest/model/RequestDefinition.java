package com.example.loadtest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "request_definitions")
@Getter
@Setter
@NoArgsConstructor
public class RequestDefinition {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "load_test_id")
    private LoadTest loadTest;
    private String name;
    private String httpMethod;
    private String url;
    @JdbcTypeCode(SqlTypes.JSON)
    private java.util.Map<String, String> headers;
    private String body;
    @Enumerated(EnumType.STRING)
    private AuthenticationType authType;
    private String authSecretReference;
    private String apiKeyHeaderName;
    @Enumerated(EnumType.STRING)
    private ApiKeyLocation apiKeyLocation;
}
