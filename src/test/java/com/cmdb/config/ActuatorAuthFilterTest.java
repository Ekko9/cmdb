package com.cmdb.config;

import com.cmdb.repo.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActuatorAuthFilterTest {
    @Test
    void prometheusRequiresAuthentication() throws Exception {
        TokenService tokenService = Mockito.mock(TokenService.class);
        AuthorizationService authorizationService = new AuthorizationService(Mockito.mock(UserRepository.class));
        ActuatorAuthFilter filter = new ActuatorAuthFilter(tokenService, authorizationService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        Mockito.verifyNoInteractions(chain);
    }

    @Test
    void readinessIsPublic() throws Exception {
        TokenService tokenService = Mockito.mock(TokenService.class);
        AuthorizationService authorizationService = new AuthorizationService(Mockito.mock(UserRepository.class));
        ActuatorAuthFilter filter = new ActuatorAuthFilter(tokenService, authorizationService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/readiness");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Mockito.verify(chain).doFilter(request, response);
    }
}
