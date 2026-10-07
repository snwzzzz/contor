package com.example.contor.service;

import com.example.contor.api.model.VersionDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SystemService {

    private final String name;
    private final String version;

    public SystemService(@Value("${app.name}") String name, @Value("${app.version}") String version) {
        this.name = name;
        this.version = version;
    }

    public VersionDto getVersion() {
        return new VersionDto(name, version);
    }
}
