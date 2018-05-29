package istc.bigdawg.rest;

import istc.bigdawg.exceptions.BigDawgCatalogException;
import istc.bigdawg.utils.Tuple;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

class OAuth2 {
    private final static int ConnectTimeout = 20000; // ms
    private final static int ReadTimeout = 20000; // ms
    private final static int DefaultAuthTimeout = 3600; // seconds
    private static Logger log = Logger
            .getLogger(OAuth2.class.getName());

    private final static Map<String, Tuple.Tuple2<Long, String>> tokenStorage = new HashMap<>();

    enum AuthenticationType {
        BASIC_BEARER
    }

    enum ResponseType {
        JSON
    }

    private static AuthenticationType getAuthenticationTypeFromString(String authenticationTypeStr) throws BigDawgCatalogException {
        if (authenticationTypeStr == null) {
            return AuthenticationType.BASIC_BEARER;
        }

        switch (authenticationTypeStr) {
            case "basic_bearer":
            case "Basic_Bearer":
            case "BASIC_BEARER":
                return AuthenticationType.BASIC_BEARER;
            default:
                throw new BigDawgCatalogException("Unknown / Unsupported OAuth2 authentication type: " + authenticationTypeStr);
        }
    }

    static void setOAuth2Headers(Map<String, String> headers, Map<String, String> connectionParameters, String user, String password) {
        try {
            AuthenticationType oauth2AuthType = getAuthenticationTypeFromString(connectionParameters.get("oauth2_auth_type"));
            if (oauth2AuthType == null) {
                oauth2AuthType = AuthenticationType.BASIC_BEARER;
            }
            switch(oauth2AuthType) {
                case BASIC_BEARER:
                    String url = connectionParameters.get("auth_url");
                    String credentials = user + ":" + password;
                    String credentialsBase64 = new String(Base64.getEncoder().encode(credentials.getBytes()));
                    HttpMethod method = HttpMethod.GET;
                    if (connectionParameters.containsKey("auth_method")) {
                        method = HttpMethod.parseMethod(connectionParameters.get("auth_method"));
                        assert(method != null);
                    }
                    String postData = null;
                    if (method == HttpMethod.POST) {
                        // See if there's POST data
                        if (connectionParameters.containsKey("auth_post_data")) {
                            postData = connectionParameters.get("auth_post_data");
                            postData = URLDecoder.decode(postData, "UTF-8");
                        }
                    }
                    ResponseType responseType = ResponseType.JSON;
                    String responseKey = connectionParameters.getOrDefault("auth_response_key","access_token");
                    Map<String, String> validationPairs = parseAuthResponseValidate(connectionParameters.getOrDefault("auth_response_validate", null));
                    String postMimeType = connectionParameters.getOrDefault("auth_post_mime_type", "application/x-www-form-urlencoded");

                    long timestamp = Instant.now().getEpochSecond();
                    String cacheKey = getBearerTokenCacheKey(url, method, responseType, credentialsBase64, postData, postMimeType);

                    // Check to see if token is cached
                    String token = null;
                    if (OAuth2.tokenStorage.containsKey(cacheKey)) {
                        Tuple.Tuple2<Long, String> tokenTuple = OAuth2.tokenStorage.get(cacheKey);
                        if (timestamp < tokenTuple.getT1()) {
                            token = tokenTuple.getT2();
                        }
                    }

                    if (token == null) { // need to refresh token
                        token = getBearerTokenFromURL(url, method, responseType, credentialsBase64, postData, postMimeType, responseKey, validationPairs);

                        // Be a good citizen and cache the token
                        int timestampOffset = Integer.parseInt(connectionParameters.getOrDefault("auth_timeout", String.valueOf(DefaultAuthTimeout)));
                        Tuple.Tuple2<Long, String> tokenTuple = new Tuple.Tuple2<>(timestamp + timestampOffset, token);
                        OAuth2.tokenStorage.put(cacheKey, tokenTuple);
                    }

                    headers.put("Authorization", "Bearer " + token);
                    break;
            }
        }
        catch (Exception e) {
            OAuth2.log.error("Exception trying to obtain bearer token", e);
        }
    }

    private static String getBearerTokenCacheKey(String urlStr, HttpMethod method, ResponseType responseType, String credentials, String postData, String postMimeType) {
        return urlStr +
            "####" +
            method.name() +
            "####" +
            responseType.name() +
            "####" +
            credentials +
            "####" +
            (postData == null ? "" : postData) +
            "####" +
            postMimeType;
    }

    private static String getBearerTokenFromURL(String urlStr, HttpMethod method, ResponseType responseType, String credentials, String postData, String postMimeType, String responseKey, Map<String, String> validationPairs) throws IOException, ParseException {

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + credentials);
        if (postData != null) {
            assert(method == HttpMethod.POST);
            headers.put("Content-Type", postMimeType);
        }

        assert(responseType == ResponseType.JSON); // We don't support any other right now.

        String response = URLUtil.fetch(urlStr, method, headers, postData, ConnectTimeout, ReadTimeout);
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(response);
        for(String key: validationPairs.keySet()) {
            String result = (String) jsonObject.get(key);
            String expectedResult = validationPairs.get(key);
            if (!expectedResult.equals(result)) {
                throw new ParseException(-1 , "Result not as expected - expected '" + key + "=" + expectedResult + "' instead got result: '" + (result == null ? "(null)" : result));
            }
        }

        String value = (String) jsonObject.get(responseKey);
        if (value == null) {
            throw new ParseException(-1, "Could not find '" + responseKey + "' in result: " + response);
        }
        return value;
    }

    static void verifyConnectionParameters(Map<String, String> connectionParameters, String user, String password) throws BigDawgCatalogException {
        if (!connectionParameters.containsKey("auth_url")) {
            throw new BigDawgCatalogException("For oauth2, need an auth_url");
        }
        AuthenticationType oauth2AuthType = getAuthenticationTypeFromString(connectionParameters.get("oauth2_auth_type"));
        if (oauth2AuthType == null) {
            oauth2AuthType = AuthenticationType.BASIC_BEARER;
        }

        switch(oauth2AuthType) {
            case BASIC_BEARER:
                if (user == null || user.length() == 0) {
                    throw new BigDawgCatalogException("For oauth2, need a user in the database");
                }
                if (password == null || password.length() == 0) {
                    throw new BigDawgCatalogException("For oauth2, need a user in the database");
                }
                break;
            default:
                throw new BigDawgCatalogException("Unknown / Unsupported OAuth2 authentication type: " + oauth2AuthType.name());
        }
        if (connectionParameters.containsKey("auth_method")) {
            String authMethodStr = connectionParameters.get("auth_method");
            HttpMethod authMethod = HttpMethod.parseMethod(authMethodStr);
            if (authMethod == null) {
                throw new BigDawgCatalogException("Unknown / Unsupported auth_method: " + authMethodStr);
            }
        }

        if (connectionParameters.containsKey("auth_response_type")) {
            if (!connectionParameters.get("auth_response_type").toUpperCase().equals("JSON")) {
                throw new BigDawgCatalogException("Unknown / Unsupported response type: " + connectionParameters.get("auth_response_type"));
            }
        }
        if (connectionParameters.containsKey("auth_response_validate")) {
            String responseValidationPairs = connectionParameters.get("auth_response_validate");
            parseAuthResponseValidate(responseValidationPairs);
        }
    }

    private static Map<String, String> parseAuthResponseValidate(String responseValidationPairs) throws BigDawgCatalogException {
        Map<String, String> resultPairs = new HashMap<>();
        if (responseValidationPairs == null) {
            return resultPairs;
        }

        try {
            responseValidationPairs = URLDecoder.decode(responseValidationPairs, "UTF-8");
            String [] pairs = responseValidationPairs.split("&");
            for(String pair: pairs) {
                String [] keyValue = pair.split("=");
                if (keyValue.length != 2) {
                    throw new BigDawgCatalogException("Expected a key=value pair from auth_response_validate: " + pair);
                }
                String key = URLDecoder.decode(keyValue[0], "UTF-8");
                String value = URLDecoder.decode(keyValue[1], "UTF-8");
                if (resultPairs.containsKey(key)) {
                    throw new BigDawgCatalogException("Duplicate key found: " + key);
                }
                resultPairs.put(key, value);
            }
        }
        catch (Exception e) {
            throw new BigDawgCatalogException("Exception while decoding responseValidationPairs '" + responseValidationPairs + "': " + e.getMessage());
        }
        return resultPairs;
    }

}