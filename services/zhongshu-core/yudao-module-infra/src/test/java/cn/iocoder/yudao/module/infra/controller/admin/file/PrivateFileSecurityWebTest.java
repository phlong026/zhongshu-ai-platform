package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.config.YudaoWebSecurityConfigurerAdapter;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.security.core.handler.AccessDeniedHandlerImpl;
import cn.iocoder.yudao.framework.security.core.handler.AuthenticationEntryPointImpl;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.security.TenantSecurityWebFilter;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.web.TenantContextWebFilter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.infra.service.file.PrivateFileService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the production Spring Security/token filter configuration, not a mock authenticated filter. */
@SpringJUnitWebConfig(PrivateFileSecurityWebTest.Config.class)
class PrivateFileSecurityWebTest {

    private static final String ID = "b08d10d0-d4ad-4d84-958f-f7a0c6231fa0";
    private static final String DOWNLOAD = "/admin-api/infra/private-file/" + ID;
    private static final byte[] PUBLIC = "public fixture".getBytes(StandardCharsets.UTF_8);
    @Resource private WebApplicationContext context;
    @Resource private FilterChainProxy springSecurityFilterChain;
    @Resource private PrivateFileService privateFiles;
    @Resource private FileService publicFiles;
    @Resource private OAuth2TokenCommonApi tokens;
    @Resource private GlobalExceptionHandler exceptions;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(privateFiles, publicFiles, tokens);
        TenantSecurityWebFilter tenantSecurity = new TenantSecurityWebFilter(new WebProperties(), new TenantProperties(),
                Set.of("/admin-api/infra/file/*/get/**"), exceptions, mock(TenantFrameworkService.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new TenantContextWebFilter(), springSecurityFilterChain, tenantSecurity).build();
        when(tokens.checkAccessToken("valid-fixture-token")).thenReturn(new OAuth2AccessTokenCheckRespDTO()
                .setUserId(101L).setUserType(UserTypeEnum.ADMIN.getValue()).setTenantId(10L));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousMultipartUploadIsRejectedBeforeController() throws Exception {
        mvc.perform(multipart("/admin-api/infra/private-file/upload")
                        .file(new MockMultipartFile("file", "fixture.txt", "text/plain", PUBLIC))
                        .param("objectType", "fixture").param("objectId", "9").param("purpose", "evidence")
                        .header("tenant-id", "10"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(privateFiles);
    }

    @Test
    void anonymousKnownFileIdIsRejectedBeforeController() throws Exception {
        mvc.perform(get(DOWNLOAD).header("tenant-id", "10")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(privateFiles);
    }

    @Test
    void invalidTokenDoesNotAuthenticatePrivateDownload() throws Exception {
        mvc.perform(get(DOWNLOAD).servletPath(DOWNLOAD).header("tenant-id", "10")
                        .header("Authorization", "Bearer expired-fixture-token"))
                .andExpect(jsonPath("$.code").value(401));
        verify(tokens).checkAccessToken("expired-fixture-token");
        verifyNoInteractions(privateFiles);
    }

    @Test
    void authenticatedForgedTenantCannotReachPrivateController() throws Exception {
        mvc.perform(get(DOWNLOAD).servletPath(DOWNLOAD).header("tenant-id", "20")
                        .header("Authorization", "Bearer valid-fixture-token"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(privateFiles);
    }

    @Test
    void authenticatedRequestReachesPrivateService() throws Exception {
        when(privateFiles.download(ID)).thenReturn(new PrivateFileService.Download("fixture.txt", PUBLIC));
        mvc.perform(get(DOWNLOAD).servletPath(DOWNLOAD).header("tenant-id", "10")
                        .header("Authorization", "Bearer valid-fixture-token"))
                .andExpect(status().isOk()).andExpect(content().bytes(PUBLIC))
                .andExpect(header().string("Cache-Control", "no-store, private"));
        verify(privateFiles).download(ID);
    }

    @Test
    void publicDownloadStillWorksWithoutTokenOrTenant() throws Exception {
        when(publicFiles.getFileContent(7L, "public.txt")).thenReturn(PUBLIC);
        mvc.perform(get("/admin-api/infra/file/7/get/public.txt"))
                .andExpect(status().isOk()).andExpect(content().bytes(PUBLIC));
        verifyNoInteractions(privateFiles);
        verify(publicFiles).getFileContent(7L, "public.txt");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({YudaoWebSecurityConfigurerAdapter.class, PrivateFileController.class, FileController.class})
    static class Config implements WebMvcConfigurer {
        @Override
        public void configurePathMatch(PathMatchConfigurer configurer) {
            configurer.addPathPrefix("/admin-api", type -> type.getPackageName().contains(".controller.admin."));
        }
        @Bean WebProperties webProperties() { return new WebProperties(); }
        @Bean WebFrameworkUtils webFrameworkUtils(WebProperties properties) { return new WebFrameworkUtils(properties); }
        @Bean SecurityProperties securityProperties() { return new SecurityProperties(); }
        @Bean PrivateFileService privateFiles() { return mock(PrivateFileService.class); }
        @Bean FileService publicFiles() { return mock(FileService.class); }
        @Bean OAuth2TokenCommonApi tokens() { return mock(OAuth2TokenCommonApi.class); }
        @Bean GlobalExceptionHandler exceptions() {
            return new GlobalExceptionHandler("private-file-security-test", mock(ApiErrorLogCommonApi.class));
        }
        @Bean AuthenticationEntryPointImpl authenticationEntryPoint() { return new AuthenticationEntryPointImpl(); }
        @Bean AccessDeniedHandlerImpl accessDeniedHandler() { return new AccessDeniedHandlerImpl(); }
        @Bean TokenAuthenticationFilter tokenFilter(SecurityProperties properties, GlobalExceptionHandler exceptions,
                                                    OAuth2TokenCommonApi tokens) {
            return new TokenAuthenticationFilter(properties, exceptions, tokens);
        }
        @Bean AuthorizeRequestsCustomizer noAdditionalPermitAll() {
            return new AuthorizeRequestsCustomizer() {
                @Override
                public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                    // No test-specific authentication bypasses.
                }
            };
        }
    }

}
