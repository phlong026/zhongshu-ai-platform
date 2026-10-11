package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.PrivateFileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.PrivateFileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrivateFileServiceImplTest {

    private static final String ID = "b08d10d0-d4ad-4d84-958f-f7a0c6231fa0";
    private static final byte[] CONTENT = "private fixture".getBytes(StandardCharsets.UTF_8);
    private PrivateFileMapper mapper;
    private PrivateFileObjectAuthorizer authorizer;
    private DefaultListableBeanFactory beanFactory;
    private PrivateFileServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(PrivateFileMapper.class);
        authorizer = mock(PrivateFileObjectAuthorizer.class);
        beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("objectAuthorizer", authorizer);
        service = new PrivateFileServiceImpl(mapper, beanFactory.getBeanProvider(PrivateFileObjectAuthorizer.class));
        login(10L, 101L);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousCannotReadOrUploadEvenWithTenantHeader() {
        SecurityContextHolder.clearContext();
        assertDenied(401, () -> service.download(ID));
        assertDenied(401, this::upload);
        verifyNoInteractions(mapper, authorizer);
    }

    @Test
    void forgedTenantCannotReadOrUpload() {
        TenantContextHolder.setTenantId(20L);
        assertDenied(403, () -> service.download(ID));
        assertDenied(403, this::upload);
        verifyNoInteractions(mapper, authorizer);
    }

    @Test
    void missingTenantFailsClosed() {
        TenantContextHolder.clear();
        assertDenied(403, this::upload);
        verifyNoInteractions(mapper, authorizer);
    }

    @Test
    void tenantVisitIsNotAnObjectAuthorization() {
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.setVisitTenantId(20L);
        TenantContextHolder.setTenantId(20L);
        when(authorizer.isAllowed(any())).thenReturn(true);
        assertDenied(403, () -> service.download(ID));
        assertDenied(403, this::upload);
        verifyNoInteractions(mapper);
        verify(authorizer, never()).isAllowed(any());
    }

    @Test
    void memberIdentityCannotUseSystemTechnicalAttachmentEntry() {
        LoginUser user = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.setUserType(UserTypeEnum.MEMBER.getValue());
        assertDenied(403, this::upload);
        verifyNoInteractions(mapper, authorizer);
    }

    @Test
    void missingObjectAdapterDeniesBothActions() {
        beanFactory.destroySingleton("objectAuthorizer");
        when(mapper.selectMetadata(ID, 10L)).thenReturn(metadata(10L));
        assertDenied(403, this::upload);
        assertDenied(403, () -> service.download(ID));
        verify(mapper, never()).insert(any(PrivateFileDO.class));
        verify(mapper, never()).selectContent(anyString(), anyLong());
    }

    @Test
    void ambiguousObjectAdaptersFailClosed() {
        beanFactory.registerSingleton("anotherAuthorizer", (PrivateFileObjectAuthorizer) access -> true);
        when(authorizer.isAllowed(any())).thenReturn(true);
        assertDenied(403, this::upload);
        verifyNoInteractions(mapper);
    }

    @Test
    void knownOtherTenantIdFailsBeforeObjectPolicyAndBytes() {
        // Defense in depth even if a broken or ignored tenant plugin returns another tenant row.
        when(mapper.selectMetadata(ID, 10L)).thenReturn(metadata(20L));
        assertDenied(403, () -> service.download(ID));
        verifyNoInteractions(authorizer);
        verify(mapper, never()).selectContent(anyString(), anyLong());
    }

    @Test
    void sameTenantKnownOtherObjectIdDoesNotGrantAccess() {
        when(mapper.selectMetadata(ID, 10L)).thenReturn(metadata(10L));
        assertDenied(403, () -> service.download(ID));
        verify(authorizer).isAllowed(new PrivateFileObjectAuthorizer.Access(10L, 101L,
                "fixture", "object-9", "evidence", PrivateFileObjectAuthorizer.Action.DOWNLOAD));
        verify(mapper, never()).selectContent(anyString(), anyLong());
    }

    @Test
    void unknownFileAndDeniedFileHaveSameRefusal() {
        ServiceException missing = assertThrows(ServiceException.class, () -> service.download(ID));
        when(mapper.selectMetadata(ID, 10L)).thenReturn(metadata(10L));
        ServiceException denied = assertThrows(ServiceException.class, () -> service.download(ID));
        assertEquals(missing.getCode(), denied.getCode());
        assertEquals(missing.getMessage(), denied.getMessage());
    }

    @Test
    void validUploadUsesServerSubjectAndObjectPolicy() {
        when(authorizer.isAllowed(any())).thenReturn(true);
        String id = upload();
        assertNotNull(UUID.fromString(id));
        verify(authorizer).isAllowed(new PrivateFileObjectAuthorizer.Access(10L, 101L,
                "fixture", "object-9", "evidence", PrivateFileObjectAuthorizer.Action.UPLOAD));
        verify(mapper).insert(argThat((PrivateFileDO file) -> file.getTenantId().equals(10L)
                && file.getId().equals(id) && file.getSize() == CONTENT.length
                && file.getType().equals("text/plain") && file.getObjectId().equals("object-9")));
    }

    @Test
    void revocationIsCheckedOnEveryDownloadIncludingUploader() {
        when(mapper.selectMetadata(ID, 10L)).thenReturn(metadata(10L));
        PrivateFileDO content = metadata(10L);
        content.setContent(CONTENT);
        when(mapper.selectContent(ID, 10L)).thenReturn(content);
        when(authorizer.isAllowed(any())).thenReturn(true, false);
        assertArrayEquals(CONTENT, service.download(ID).content());
        assertDenied(403, () -> service.download(ID));
        verify(mapper, times(1)).selectContent(ID, 10L);
        verify(authorizer, times(2)).isAllowed(any());
    }

    @Test
    void tenantIgnoreCannotChangeExplicitTenantPredicate() {
        TenantContextHolder.setIgnore(true);
        assertDenied(403, () -> service.download(ID));
        verify(mapper).selectMetadata(ID, 10L);
        verify(mapper, never()).selectContent(anyString(), anyLong());
    }

    @Test
    void sizeTypeAndPurposeValidationFailBeforePersisting() {
        when(authorizer.isAllowed(any())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.upload("fixture", "9", "evidence", "empty.txt", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> service.upload("fixture", "9", "evidence", "large.txt",
                new byte[PrivateFileService.MAX_FILE_SIZE + 1]));
        assertThrows(IllegalArgumentException.class, () -> service.upload("fixture", "9", "evidence", "fake.pdf", CONTENT));
        assertThrows(IllegalArgumentException.class, () -> service.upload("fixture", "9", "evidence", "page.html",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> service.upload("fixture", "9", "", "a.txt", CONTENT));
        assertThrows(IllegalArgumentException.class, () -> service.upload("fixture", "9", "evidence", "bad\r\n.txt", CONTENT));
        verify(mapper, never()).insert(any(PrivateFileDO.class));
    }

    private String upload() {
        return service.upload("fixture", "object-9", "evidence", "evidence.txt", CONTENT);
    }

    private static PrivateFileDO metadata(Long tenantId) {
        PrivateFileDO file = new PrivateFileDO();
        file.setId(ID);
        file.setTenantId(tenantId);
        file.setObjectType("fixture");
        file.setObjectId("object-9");
        file.setPurpose("evidence");
        file.setName("evidence.txt");
        return file;
    }

    static void login(Long tenantId, Long userId) {
        LoginUser user = new LoginUser().setId(userId).setTenantId(tenantId).setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        TenantContextHolder.setTenantId(tenantId);
    }

    private static void assertDenied(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ServiceException.class, action).getCode());
    }

}
