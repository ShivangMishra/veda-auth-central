/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package com.veda.central.api.identity;

import com.veda.central.core.credential.store.api.Credentials;
import com.veda.central.core.identity.api.AuthToken;
import com.veda.central.core.identity.api.AuthenticationRequest;
import com.veda.central.core.identity.api.Claim;
import com.veda.central.core.identity.api.GetOIDCConfiguration;
import com.veda.central.core.identity.api.GetTokenRequest;
import com.veda.central.core.identity.api.GetUserManagementSATokenRequest;
import com.veda.central.core.identity.api.IsAuthenticatedResponse;
import com.veda.central.core.identity.api.OIDCConfiguration;
import com.veda.central.core.identity.api.OperationStatus;
import com.veda.central.core.identity.api.TokenResponse;
import com.veda.central.core.identity.api.User;
import com.veda.central.core.identity.management.api.AuthorizationRequest;
import com.veda.central.core.identity.management.api.AuthorizationResponse;
import com.veda.central.core.identity.management.api.EndSessionRequest;
import com.veda.central.core.identity.management.api.GetCredentialsRequest;
import com.veda.central.service.auth.AuthClaim;
import com.veda.central.service.auth.TokenAuthorizer;
import com.veda.central.service.management.IdentityManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/identity-management")
@Tag(name = "Identity Management")
public class IdentityManagementController {

    private final IdentityManagementService identityManagementService;
    private final TokenAuthorizer tokenAuthorizer;

    public IdentityManagementController(IdentityManagementService identityManagementService, TokenAuthorizer tokenAuthorizer) {
        this.identityManagementService = identityManagementService;
        this.tokenAuthorizer = tokenAuthorizer;
    }

    @PostMapping("/authenticate")
    @Operation(
            summary = "User Authentication",
            description = "Authenticates the user by verifying the provided request credentials. If authenticated successfully, " +
                    "returns an AuthToken which includes authentication details and associated claims.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schemaProperties = {
                                    @SchemaProperty(
                                            name = "username",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "The user's username"
                                            )
                                    ),
                                    @SchemaProperty(
                                            name = "password",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "The user's password"
                                            )
                                    )
                            }
                    )
            ),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Successful operation",
                            content = @Content(
                                    schemaProperties = {
                                            @SchemaProperty(
                                                    name = "access_token",
                                                    schema = @Schema(
                                                            type = "string",
                                                            description = "Access Token"
                                                    )
                                            ),
                                            @SchemaProperty(
                                                    name = "claims",
                                                    array = @ArraySchema(
                                                            schema = @Schema(implementation = Claim.class),
                                                            arraySchema = @Schema(description = "List of Claims")
                                                    )
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = AuthToken.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized Request", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<AuthToken> authenticate(@RequestBody AuthenticationRequest request, @RequestHeader HttpHeaders headers) {
        Optional<AuthClaim> claim = tokenAuthorizer.authorize(headers);

        if (claim.isPresent()) {
            AuthClaim authClaim = claim.get();
            request.toBuilder().setTenantId(authClaim.getTenantId())
                    .setClientId(authClaim.getIamAuthId())
                    .setClientSecret(authClaim.getIamAuthSecret());
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request is not authorized");
        }

        AuthToken response = identityManagementService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/authenticate/status")
    @Operation(
            summary = "Authentication Status Check",
            description = "Checks the authentication status based on the provided AuthToken. Returns an IsAuthenticatedResponse portraying the status.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schemaProperties = {
                                    @SchemaProperty(
                                            name = "access_token",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "Access Token"
                                            )
                                    ),
                                    @SchemaProperty(
                                            name = "claims",
                                            array = @ArraySchema(
                                                    schema = @Schema(implementation = Claim.class),
                                                    arraySchema = @Schema(description = "List of Claims")
                                            )
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = Boolean.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized Request", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<Boolean> isAuthenticated(@RequestBody AuthToken request, @RequestHeader HttpHeaders headers) {
        IsAuthenticatedResponse response = identityManagementService.isAuthenticated(generateAuthTokenRequest(request.toBuilder(), headers).build());
        return ResponseEntity.ok(response.getAuthenticated());
    }

    @GetMapping("/user")
    @Operation(
            summary = "Retrieve User Information",
            description = "Retrieves User Information using the provided access token. Returns a User object containing user details.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = User.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized Request", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<User> getUser(@Parameter(description = "Access Token used for authentication", required = true)
                                        @RequestParam("access_token") String accessToken, @RequestHeader HttpHeaders headers) {
        AuthToken.Builder builder = AuthToken.newBuilder().setAccessToken(accessToken);
        User response = identityManagementService.getUser(generateAuthTokenRequest(builder, headers).build());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/token")
    @Operation(
            summary = "Get User Management Service Account Access Token",
            description = "Retrieves the User Management Service Account Access Token using the provided GetUserManagementSATokenRequest. " +
                    "Returns an AuthToken for the user management service account.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Successful operation",
                            content = @Content(
                                    schemaProperties = {
                                            @SchemaProperty(
                                                    name = "access_token",
                                                    schema = @Schema(
                                                            type = "string",
                                                            description = "Access Token"
                                                    )
                                            ),
                                            @SchemaProperty(
                                                    name = "claims",
                                                    array = @ArraySchema(
                                                            schema = @Schema(implementation = Claim.class),
                                                            arraySchema = @Schema(description = "List of Claims")
                                                    )
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = AuthToken.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized Request", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<AuthToken> getUserManagementServiceAccountAccessToken(
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @RequestHeader HttpHeaders headers) {

        Optional<AuthClaim> claim = tokenAuthorizer.authorize(headers);
        GetUserManagementSATokenRequest.Builder builder = GetUserManagementSATokenRequest.newBuilder();
        if (claim.isPresent()) {
            AuthClaim authClaim = claim.get();
            builder.setTenantId(authClaim.getTenantId())
                    .setClientId(authClaim.getIamAuthId())
                    .setClientSecret(authClaim.getIamAuthSecret());
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request is not authorized");
        }

        AuthToken response = identityManagementService.getUserManagementServiceAccountAccessToken(builder.build());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/user/logout")
    @Operation(
            summary = "End User Session",
            description = "Ends the user session based on the provided EndSessionRequest. " +
                    "Returns an OperationStatus response confirming the action.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schemaProperties = {
                                    @SchemaProperty(
                                            name = "refresh_token",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "Refresh Token"
                                            )
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = Boolean.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<Boolean> endUserSession(@RequestBody Map<String, String> requestData, @RequestHeader HttpHeaders headers) {
        Optional<AuthClaim> claim = tokenAuthorizer.authorize(headers);
        String refreshToken = requestData.get("refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing or empty refresh_token");
        }

        if (claim.isPresent()) {
            AuthClaim authClaim = claim.get();
            com.veda.central.core.identity.api.EndSessionRequest endSessionRequest = com.veda.central.core.identity.api.EndSessionRequest.newBuilder()
                    .setTenantId(authClaim.getTenantId())
                    .setClientId(authClaim.getIamAuthId())
                    .setClientSecret(authClaim.getIamAuthSecret())
                    .setRefreshToken(refreshToken)
                    .build();
            EndSessionRequest request = EndSessionRequest.newBuilder().setBody(endSessionRequest).build();
            OperationStatus response = identityManagementService.endUserSession(request);

            return ResponseEntity.ok(response.getStatus());

        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request is not authorized");
        }
    }

    @GetMapping("/authorize")
    @Operation(
            summary = "Authorize User",
            description = "Authorizes the user by verifying the provided AuthorizationRequest. If authorized, an AuthorizationResponse is returned.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = AuthorizationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<AuthorizationResponse> authorize(
            @RequestParam(value = "client_id") String clientId,
            @RequestParam(value = "redirect_uri") String redirectUri,
            @RequestParam(value = "tenant_id", required = false) int tenantId) {

        AuthorizationRequest request = AuthorizationRequest.newBuilder()
                .setClientId(clientId)
                .setRedirectUri(redirectUri)
                .setTenantId(tenantId)
                .build();
        AuthorizationResponse response = identityManagementService.authorize(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get Token",
            description = "Retrieves a token based on the provided request. For basic authentication, use 'user_name' and 'password'; for authorization code grant flow, use 'code'. If successful, returns a TokenResponse.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schemaProperties = {
                                    @SchemaProperty(
                                            name = "code",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "Authorization Code",
                                                    example = "wsxdcfvgbg"
                                            )
                                    ),
                                    @SchemaProperty(
                                            name = "redirect_uri",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "Redirect URI",
                                                    example = "https://domain/callback"
                                            )
                                    ),
                                    @SchemaProperty(
                                            name = "grant_type",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "Grant Type",
                                                    example = "authorization_code"
                                            )
                                    ),
                                    @SchemaProperty(
                                            name = "username",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "User Name"
                                            )
                                    ),
                                    @SchemaProperty(
                                            name = "password",
                                            schema = @Schema(
                                                    type = "string",
                                                    description = "User's password"
                                            )
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = TokenResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized Request", content = @Content()),
                    @ApiResponse(responseCode = "404", description = "When the associated Tenant cannot be found", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<TokenResponse> token(@RequestBody GetTokenRequest request, @RequestHeader HttpHeaders headers) {
        // Expects the Base64 encoded value 'clientId:clientSecret' for Authorization Header
        Optional<AuthClaim> claim = tokenAuthorizer.authorize(headers);

        if (claim.isPresent()) {
            AuthClaim authClaim = claim.get();
            request = request.toBuilder().setTenantId(authClaim.getTenantId())
                    .setClientId(authClaim.getIamAuthId())
                    .setClientSecret(authClaim.getIamAuthSecret())
                    .build();
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request is not authorized");
        }

        TokenResponse response = identityManagementService.token(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credentials")
    @Operation(
            summary = "Get Credentials",
            description = "Retrieves credentials based on the provided client_id. Returns a Credentials object.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = Credentials.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content()),
                    @ApiResponse(responseCode = "401", description = "Unauthorized Request", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<Credentials> getCredentials(@RequestParam(value = "client_id") String clientId, @RequestHeader HttpHeaders headers) {
        Optional<AuthClaim> claim = tokenAuthorizer.authorize(headers, clientId);

        if (claim.isPresent()) {
            AuthClaim authClaim = claim.get();
            Credentials credentials = Credentials.newBuilder()
                    .setVedaClientId(authClaim.getVedaId())
                    .setVedaClientSecret(authClaim.getVedaSecret())
                    .setVedaClientIdIssuedAt(authClaim.getVedaIdIssuedAt())
                    .setVedaClientSecretExpiredAt(authClaim.getVedaSecretExpiredAt())
                    .setCiLogonClientId(authClaim.getCiLogonId())
                    .setCiLogonClientSecret(authClaim.getCiLogonSecret())
                    .setIamClientId(authClaim.getIamAuthId())
                    .setIamClientSecret(authClaim.getIamAuthSecret())
                    .build();
            GetCredentialsRequest request = GetCredentialsRequest.newBuilder().setCredentials(credentials).build();
            Credentials response = identityManagementService.getCredentials(request);
            return ResponseEntity.ok(response);

        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request is not authorized");
        }
    }

    @GetMapping("/.well-known/openid-configuration")
    @Operation(
            summary = "Get OIDC Configuration",
            description = "Retrieves the OpenID Connect (OIDC) configuration using the provided GetOIDCConfiguration request. " +
                    "Returns an OIDCConfiguration object.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = OIDCConfiguration.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized Request", content = @Content()),
                    @ApiResponse(responseCode = "404", description = "When the associated Tenant or Credentials cannot be found", content = @Content()),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content())
            }
    )
    public ResponseEntity<OIDCConfiguration> getOIDCConfiguration(@RequestParam(value = "client_id") String clientId) {
        GetOIDCConfiguration request = GetOIDCConfiguration.newBuilder()
                .setClientId(clientId)
                .build();
        OIDCConfiguration response = identityManagementService.getOIDCConfiguration(request);
        return ResponseEntity.ok(response);
    }


    private AuthToken.Builder generateAuthTokenRequest(AuthToken.Builder builder, HttpHeaders headers) {
        Optional<AuthClaim> claim = tokenAuthorizer.authorize(headers);
        if (claim.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request is not authorized");
        }

        Optional<AuthClaim> opAuthClaim = tokenAuthorizer.authorizeUsingUserToken(builder.getAccessToken());

        if (opAuthClaim.isPresent()) {
            AuthClaim authClaim = claim.get();
            Claim userClaim = Claim.newBuilder().setKey("username").setValue(authClaim.getUsername()).build();
            Claim tenantClaim = Claim.newBuilder().setKey("tenantId").setValue(String.valueOf(authClaim.getTenantId())).build();
            Claim clientClaim = Claim.newBuilder().setKey("clientId").setValue(String.valueOf(authClaim.getVedaId())).build();

            builder.addClaims(userClaim);
            builder.addClaims(tenantClaim);
            builder.addClaims(clientClaim);

            return builder;

        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request is not authorized, User token not found");
        }
    }
}
