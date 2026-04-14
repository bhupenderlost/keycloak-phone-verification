package dev.bhupender.smsauth.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OAuthError {

    private OAuthError() {
    }

    public static Response badRequest(String error, String description) {
        return response(Response.Status.BAD_REQUEST, error, description);
    }

    public static Response unauthorized(String error, String description) {
        return response(Response.Status.UNAUTHORIZED, error, description);
    }

    public static Response forbidden(String error, String description) {
        return response(Response.Status.FORBIDDEN, error, description);
    }

    public static Response tooManyRequests(String error, String description) {
        return response(Response.Status.TOO_MANY_REQUESTS, error, description);
    }

    public static Response serverError(String description) {
        return response(Response.Status.INTERNAL_SERVER_ERROR, "server_error", description);
    }

    public static Response response(Response.Status status, String error, String description) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("error", error);
        entity.put("error_description", description);
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .entity(entity)
                .build();
    }
}
