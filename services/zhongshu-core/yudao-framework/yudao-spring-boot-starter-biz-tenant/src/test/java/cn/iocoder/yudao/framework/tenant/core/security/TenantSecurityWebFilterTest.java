package cn.iocoder.yudao.framework.tenant.core.security;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.web.TenantContextWebFilter;
import cn.iocoder.yudao.framework.tenant.core.web.TenantVisitContextInterceptor;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the real request header -> tenant context -> security filter sequence. */
class TenantSecurityWebFilterTest {

    private TenantProperties properties;
    private TenantFrameworkService tenants;
    private TenantSecurityWebFilter filter;

    @BeforeEach
    void setUp() {
        properties = new TenantProperties();
        tenants = mock(TenantFrameworkService.class);
        filter = new TenantSecurityWebFilter(new WebProperties(), properties,
                Set.of("/admin-api/infra/file/*/get/**"), mock(GlobalExceptionHandler.class), tenants);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void forgedTenantHeaderRejectsAuthenticatedSubjectBeforeHandler() throws Exception {
        login();
        MockHttpServletRequest request = request("/admin-api/system/user/get", "20");
        request.addParameter("id", "202"); // Knowing another tenant's user ID does not help.
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain downstream = mock(FilterChain.class);
        run(request, response, downstream);
        assertCode(403, response);
        verifyNoInteractions(downstream, tenants);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void forgedHeaderIsRejectedEvenOnPublicIgnoreRoute() throws Exception {
        login();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain downstream = mock(FilterChain.class);
        run(request("/admin-api/infra/file/7/get/a.txt", "20"), response, downstream);
        assertCode(403, response);
        verifyNoInteractions(downstream);
    }

    @Test
    void missingHeaderUsesAuthenticatedTenantAndThenClearsContext() throws Exception {
        login();
        AtomicReference<Long> actual = new AtomicReference<>();
        run(request("/admin-api/system/user/get", null), new MockHttpServletResponse(),
                (req, res) -> actual.set(TenantContextHolder.getTenantId()));
        assertEquals(10L, actual.get());
        verify(tenants).validTenant(10L);
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test
    void anonymousWithoutTenantCannotReachProtectedHandler() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain downstream = mock(FilterChain.class);
        run(request("/admin-api/infra/private-file/known-id", null), response, downstream);
        assertCode(400, response);
        verifyNoInteractions(downstream);
    }

    @Test
    void publicAssetWithoutTenantRemainsCompatible() throws Exception {
        AtomicReference<Boolean> ignored = new AtomicReference<>();
        run(request("/admin-api/infra/file/7/get/public.txt", null), new MockHttpServletResponse(),
                (req, res) -> ignored.set(TenantContextHolder.isIgnore()));
        assertTrue(ignored.get());
        verifyNoInteractions(tenants);
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test
    void visitTenantRequiresDedicatedOperationsPermission() {
        login();
        TenantContextHolder.setTenantId(10L);
        SecurityFrameworkService permissions = mock(SecurityFrameworkService.class);
        TenantVisitContextInterceptor interceptor = new TenantVisitContextInterceptor(properties, permissions);
        MockHttpServletRequest request = request("/admin-api/system/user/get", "10");
        request.addHeader("visit-tenant-id", "20");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(10L, TenantContextHolder.getTenantId());
        verify(permissions).hasAnyPermissions("system:tenant:visit");
    }

    private void run(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain downstream) throws Exception {
        new TenantContextWebFilter().doFilter(request, response, (req, res) -> filter.doFilter(req, res, downstream));
    }

    private static MockHttpServletRequest request(String uri, String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (tenantId != null) {
            request.addHeader("tenant-id", tenantId);
        }
        return request;
    }

    private static void login() {
        LoginUser user = new LoginUser().setId(101L).setTenantId(10L).setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static void assertCode(int code, MockHttpServletResponse response) throws Exception {
        assertEquals(code, JsonUtils.parseObject(response.getContentAsString(), CommonResult.class).getCode());
    }

}
