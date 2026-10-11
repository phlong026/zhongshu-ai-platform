package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.util.ReflectUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.controller.admin.file.FileController;
import cn.iocoder.yudao.module.infra.controller.admin.file.PrivateFileController;
import cn.iocoder.yudao.module.infra.dal.mysql.file.PrivateFileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** H2 mechanism integration: real private/public mappers and DBFileClient, fixture-only object policy. */
@Import({PrivateFileServiceImpl.class, FileServiceImpl.class})
@SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
@Sql(scripts = "/sql/private_file_authorization.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/private_file_authorization_clean.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PrivateFileAuthorizationIntegrationTest extends BaseDbUnitTest {

    private static final byte[] CONTENT = "private attachment fixture".getBytes(StandardCharsets.UTF_8);
    @Resource private PrivateFileService privateFiles;
    @Resource private FileService publicFiles;
    @Resource private PrivateFileMapper privateMapper;
    @MockitoBean private PrivateFileObjectAuthorizer authorizer;
    @MockitoBean private FileConfigService fileConfigService;

    @BeforeEach
    void setUp() {
        PrivateFileServiceImplTest.login(10L, 101L);
        when(authorizer.isAllowed(any())).thenAnswer(invocation -> {
            PrivateFileObjectAuthorizer.Access access = invocation.getArgument(0);
            return access.tenantId() == 10L && access.userId() == 101L
                    && access.objectType().equals("fixture") && access.objectId().equals("object-9");
        });
        DBFileClient publicClient = new DBFileClient(7L, new DBFileClientConfig().setDomain("https://fixture.invalid"));
        publicClient.init();
        when(fileConfigService.getFileClient(7L)).thenReturn(publicClient);
        when(fileConfigService.getMasterFileClient()).thenReturn(publicClient);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizedRoundTripAndRevocation() {
        String id = upload();
        assertNull(privateMapper.selectMetadata(id, 10L).getContent());
        assertArrayEquals(CONTENT, privateFiles.download(id).content());
        doReturn(false).when(authorizer).isAllowed(any());
        assertForbidden(id);
    }

    @Test
    void knownFileIdCannotCrossTenantEvenWhenTenantInterceptorIgnored() {
        String id = upload();
        PrivateFileServiceImplTest.login(20L, 202L);
        TenantContextHolder.setIgnore(true);
        doReturn(true).when(authorizer).isAllowed(any());
        assertNull(privateMapper.selectMetadata(id, 20L));
        assertForbidden(id);
    }

    @Test
    void sameTenantAnotherUserRequiresObjectPermission() {
        String id = upload();
        PrivateFileServiceImplTest.login(10L, 102L);
        assertForbidden(id);
    }

    @Test
    void anonymousCannotDownloadKnownPrivateId() {
        String id = upload();
        SecurityContextHolder.clearContext();
        assertEquals(401, assertThrows(ServiceException.class, () -> privateFiles.download(id)).getCode());
    }

    @Test
    void publicDownloadCannotResolvePrivateIdOrFilename() throws Exception {
        String id = upload();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        FileController controller = new FileController();
        ReflectUtil.setFieldValue(controller, "fileService", publicFiles);
        for (String path : new String[]{id, "evidence.txt", "infra_private_file/" + id}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-api/infra/file/7/get/" + path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            controller.getFileContent(request, response, 7L);
            assertEquals(404, response.getStatus());
            assertEquals(0, response.getContentAsByteArray().length);
        }
    }

    @Test
    void publicAssetsRemainAnonymouslyReadable() throws Exception {
        publicFiles.createFile(CONTENT, "public.txt", "public-assets", "text/plain");
        // Locate the real uploaded path rather than relying on a date or static naming option.
        var file = publicFiles.getFilePage(new cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO())
                .getList().get(0);
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        FileController controller = new FileController();
        ReflectUtil.setFieldValue(controller, "fileService", publicFiles);
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.getFileContent(new MockHttpServletRequest("GET", "/admin-api/infra/file/7/get/" + file.getPath()), response, 7L);
        assertEquals(200, response.getStatus());
        assertArrayEquals(CONTENT, response.getContentAsByteArray());
    }

    @Test
    void privateDownloadIsNotCacheableOrInline() throws Exception {
        String id = upload();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new PrivateFileController(privateFiles).download(id, response);
        assertEquals("no-store, private", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
        assertArrayEquals(CONTENT, response.getContentAsByteArray());
    }

    private String upload() {
        return privateFiles.upload("fixture", "object-9", "evidence", "evidence.txt", CONTENT);
    }

    private void assertForbidden(String id) {
        assertEquals(403, assertThrows(ServiceException.class, () -> privateFiles.download(id)).getCode());
    }

}
