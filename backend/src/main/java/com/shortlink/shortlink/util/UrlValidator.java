package com.shortlink.shortlink.util;

import com.shortlink.shortlink.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

@Component
public class UrlValidator {

    private static final int MAX_URL_LENGTH = 2048;

    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("URL must not be empty");
        }

        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL exceeds " + MAX_URL_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new InvalidUrlException("Only http and https protocols are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()){
            throw new InvalidUrlException("URL must have a valid host");
        }

        if (isPrivateAddress(host)) {
            throw new InvalidUrlException("Private network addresses are not allowed");
        }
    }

    private boolean isPrivateAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
