package com.shortlink.shortlink.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

@Service
public class GeoLookupService {

    private static final Logger log = LoggerFactory.getLogger(GeoLookupService.class);

    private final DatabaseReader databaseReader;

    public GeoLookupService(@Value("${app.geoip.database-path:}") String databasePath) {
        this.databaseReader = initializeDatabaseReader(databasePath);
    }

    public GeoLocation lookup(String ipAddress) {
        if (databaseReader == null || ipAddress == null || ipAddress.isBlank()) {
            return null;
        }

        try {
            CityResponse response = databaseReader.city(InetAddress.getByName(ipAddress));
            return new GeoLocation(
                    response.getCountry() == null ? null : response.getCountry().getIsoCode(),
                    response.getCity() == null ? null : response.getCity().getName()
            );
        } catch (Exception exception) {
            return null;
        }
    }

    @PreDestroy
    void close() throws IOException {
        if (databaseReader != null) {
            databaseReader.close();
        }
    }

    private DatabaseReader initializeDatabaseReader(String databasePath) {
        if (databasePath == null || databasePath.isBlank()) {
            log.info("GeoLite2 database path is not configured. Geo lookup will be disabled.");
            return null;
        }

        File databaseFile = new File(databasePath);
        if (!databaseFile.exists() || !databaseFile.isFile()) {
            log.warn("GeoLite2 database file '{}' was not found. Geo lookup will be disabled.", databasePath);
            return null;
        }

        try {
            return new DatabaseReader.Builder(databaseFile).build();
        } catch (IOException exception) {
            log.warn("Failed to initialize GeoLite2 database from '{}'. Geo lookup will be disabled.", databasePath, exception);
            return null;
        }
    }

    public record GeoLocation(String country, String city) {
    }
}
