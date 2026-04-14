package dev.bhupender.smsauth.grant;

import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.RoleUtils;
import org.keycloak.rar.AuthorizationRequestContext;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

public final class PhoneTokenIssuer {

    private PhoneTokenIssuer() {
    }

    public static Response issue(KeycloakSession session, RealmModel realm, ClientModel client, UserModel user,
            String scope, EventBuilder event) {
        RootAuthenticationSessionModel rootAuthSession = null;
        try {
            rootAuthSession = session.authenticationSessions().createRootAuthenticationSession(realm);
            AuthenticationSessionModel authSession = rootAuthSession.createAuthenticationSession(client);
            authSession.setAuthenticatedUser(user);
            authSession.setProtocol("openid-connect");
            if (scope != null && !scope.isBlank()) {
                authSession.setClientNote("scope", scope);
            }

            UserSessionModel userSession = session.sessions().createUserSession(
                    realm,
                    user,
                    user.getUsername(),
                    session.getContext().getConnection().getRemoteAddr(),
                    "phone-otp",
                    false,
                    null,
                    null);

            AuthenticatedClientSessionModel clientSession = session.sessions().createClientSession(realm, client,
                    userSession);
            clientSession.setProtocol("openid-connect");
            if (scope != null && !scope.isBlank()) {
                clientSession.setNote("scope", scope);
            }

            ClientSessionContext clientSessionContext = new SimpleClientSessionContext(clientSession, client, user,
                    scope);

            Class<?> tokenManagerClass = Class.forName("org.keycloak.protocol.oidc.TokenManager");
            Object tokenManager = tokenManagerClass.getConstructor().newInstance();
            Object responseBuilder = tokenManagerClass
                    .getMethod("responseBuilder", RealmModel.class, ClientModel.class, EventBuilder.class,
                            KeycloakSession.class, UserSessionModel.class, ClientSessionContext.class)
                    .invoke(tokenManager, realm, client, event, session, userSession, clientSessionContext);

            invokeChain(responseBuilder, "generateAccessToken");
            invokeChain(responseBuilder, "generateRefreshToken");
            if (scope != null && scope.contains("openid")) {
                invokeChain(responseBuilder, "generateIDToken");
            }

            Object accessTokenResponse = responseBuilder.getClass().getMethod("build").invoke(responseBuilder);
            return Response.ok(accessTokenResponse).build();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to issue OIDC token response", e);
        } finally {
            if (rootAuthSession != null) {
                session.authenticationSessions().removeRootAuthenticationSession(realm, rootAuthSession);
            }
        }
    }

    private static void invokeChain(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        method.invoke(target);
    }

    private static final class SimpleClientSessionContext implements ClientSessionContext {
        private final AuthenticatedClientSessionModel clientSession;
        private final Set<ClientScopeModel> clientScopes;
        private final Set<RoleModel> roles;
        private final String scopeString;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private SimpleClientSessionContext(AuthenticatedClientSessionModel clientSession, ClientModel client,
                UserModel user,
                String requestedScope) {
            this.clientSession = clientSession;
            this.clientScopes = resolveScopes(client, requestedScope);
            this.roles = resolveRoles(client, user, clientScopes);
            this.scopeString = buildScopeString(clientScopes, requestedScope);
        }

        @Override
        public AuthenticatedClientSessionModel getClientSession() {
            return clientSession;
        }

        @Override
        public Set<String> getClientScopeIds() {
            return clientScopes.stream().map(ClientScopeModel::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Stream<ClientScopeModel> getClientScopesStream() {
            return clientScopes.stream();
        }

        @Override
        public boolean isOfflineTokenRequested() {
            return scopeString.contains("offline_access");
        }

        @Override
        public Stream<RoleModel> getRolesStream() {
            return roles.stream();
        }

        @Override
        public Stream<ProtocolMapperModel> getProtocolMappersStream() {
            Stream<ProtocolMapperModel> clientMappers = clientSession.getClient().getProtocolMappersStream();
            Stream<ProtocolMapperModel> scopeMappers = clientScopes.stream()
                    .flatMap(ClientScopeModel::getProtocolMappersStream);
            return Stream.concat(clientMappers, scopeMappers);
        }

        @Override
        public String getScopeString() {
            return scopeString;
        }

        @Override
        public String getScopeString(boolean ignoreIncludeInTokenScope) {
            return scopeString;
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getAttribute(String key, Class<T> clazz) {
            Object value = attributes.get(key);
            return value == null ? null : (T) value;
        }

        @Override
        public AuthorizationRequestContext getAuthorizationRequestContext() {
            return null;
        }

        private static Set<ClientScopeModel> resolveScopes(ClientModel client, String requestedScope) {
            Map<String, ClientScopeModel> resolved = new LinkedHashMap<>(client.getClientScopes(true));
            if (requestedScope != null) {
                for (String token : requestedScope.trim().split("\\s+")) {
                    if (token.isBlank()) {
                        continue;
                    }
                    ClientScopeModel optionalScope = client.getClientScopes(false).get(token);
                    if (optionalScope != null) {
                        resolved.put(optionalScope.getName(), optionalScope);
                    }
                }
            }
            return new LinkedHashSet<>(resolved.values());
        }

        private static Set<RoleModel> resolveRoles(ClientModel client, UserModel user,
                Set<ClientScopeModel> clientScopes) {
            Set<RoleModel> userRoles = RoleUtils.getDeepUserRoleMappings(user);
            if (client.isFullScopeAllowed()) {
                return userRoles;
            }

            Set<RoleModel> allowed = new LinkedHashSet<>();
            allowed.addAll(RoleUtils.expandCompositeRoles(client.getScopeMappingsStream().collect(Collectors.toSet())));
            for (ClientScopeModel clientScope : clientScopes) {
                allowed.addAll(RoleUtils
                        .expandCompositeRoles(clientScope.getScopeMappingsStream().collect(Collectors.toSet())));
            }

            return userRoles.stream()
                    .filter(role -> RoleUtils.hasRole(allowed, role))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        private static String buildScopeString(Set<ClientScopeModel> clientScopes, String requestedScope) {
            if (requestedScope != null && !requestedScope.isBlank()) {
                return requestedScope.trim();
            }
            return clientScopes.stream()
                    .filter(ClientScopeModel::isIncludeInTokenScope)
                    .map(ClientScopeModel::getName)
                    .collect(Collectors.joining(" "));
        }
    }
}
