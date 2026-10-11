package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.PrivateFileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.PrivateFileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.service.file.PrivateFileObjectAuthorizer.Action;

/**
 * Fail-closed foundation for small private attachments. Public file storage is unchanged.
 * No presigned/public URL, ownership claim, creator privilege or tenant-visit shortcut is used.
 * Production business use requires a reviewed object authorizer and the private-file migration.
 */
@Service
public class PrivateFileServiceImpl implements PrivateFileService {

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "application/pdf", Set.of("pdf"), "image/png", Set.of("png"),
            "image/jpeg", Set.of("jpg", "jpeg"), "text/plain", Set.of("txt"));
    private static final PrivateFileObjectAuthorizer DENY_ALL = access -> false;

    private final PrivateFileMapper fileMapper;
    private final ObjectProvider<PrivateFileObjectAuthorizer> authorizers;

    public PrivateFileServiceImpl(PrivateFileMapper fileMapper,
                                  ObjectProvider<PrivateFileObjectAuthorizer> authorizers) {
        this.fileMapper = fileMapper;
        this.authorizers = authorizers;
    }

    @Override
    public String upload(String objectType, String objectId, String purpose, String name, byte[] content) {
        LoginUser actor = requireActor();
        validateReference(objectType, objectId, purpose);
        authorize(actor, objectType, objectId, purpose, Action.UPLOAD);
        String type = validateContent(name, content);
        PrivateFileDO file = new PrivateFileDO();
        file.setId(UUID.randomUUID().toString());
        file.setTenantId(actor.getTenantId());
        file.setObjectType(objectType);
        file.setObjectId(objectId);
        file.setPurpose(purpose);
        file.setName(name);
        file.setType(type);
        file.setSize((long) content.length);
        file.setContent(content);
        fileMapper.insert(file);
        return file.getId();
    }

    @Override
    public Download download(String id) {
        LoginUser actor = requireActor();
        // Uniform refusal for unavailable and unauthorized IDs; no existence oracle.
        if (id == null || !id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            throw exception(FORBIDDEN);
        }
        PrivateFileDO file = fileMapper.selectMetadata(id, actor.getTenantId());
        if (file == null || !Objects.equals(file.getTenantId(), actor.getTenantId())) {
            throw exception(FORBIDDEN);
        }
        // Always recheck live object permissions; uploading a file is not an enduring grant.
        authorize(actor, file.getObjectType(), file.getObjectId(), file.getPurpose(), Action.DOWNLOAD);
        PrivateFileDO stored = fileMapper.selectContent(id, actor.getTenantId());
        if (stored == null || !Objects.equals(stored.getTenantId(), actor.getTenantId())
                || stored.getContent() == null) {
            throw exception(FORBIDDEN);
        }
        return new Download(file.getName(), stored.getContent());
    }

    private LoginUser requireActor() {
        LoginUser actor = SecurityFrameworkUtils.getLoginUser();
        if (actor == null || actor.getId() == null || SecurityFrameworkUtils.getAuthentication() == null
                || !SecurityFrameworkUtils.getAuthentication().isAuthenticated()) {
            throw exception(UNAUTHORIZED);
        }
        if (!Objects.equals(actor.getUserType(), UserTypeEnum.ADMIN.getValue())
                || actor.getTenantId() == null
                || !Objects.equals(actor.getTenantId(), TenantContextHolder.getTenantId())
                || (actor.getVisitTenantId() != null
                    && !Objects.equals(actor.getVisitTenantId(), actor.getTenantId()))) {
            throw exception(FORBIDDEN);
        }
        return actor;
    }

    private void authorize(LoginUser actor, String objectType, String objectId, String purpose, Action action) {
        // No bean (or no unique bean) means DENY, never a fallback to public/RBAC-only access.
        PrivateFileObjectAuthorizer authorizer = authorizers.getIfUnique(() -> DENY_ALL);
        if (!authorizer.isAllowed(new PrivateFileObjectAuthorizer.Access(actor.getTenantId(), actor.getId(),
                objectType, objectId, purpose, action))) {
            throw exception(FORBIDDEN);
        }
    }

    private void validateReference(String objectType, String objectId, String purpose) {
        if (objectType == null || !objectType.matches("[a-z][a-z0-9_-]{0,63}")
                || purpose == null || !purpose.matches("[a-z][a-z0-9_-]{0,63}")
                || objectId == null || objectId.isBlank() || objectId.length() > 128
                || objectId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid private attachment object reference or purpose");
        }
    }

    private String validateContent(String name, byte[] content) {
        if (name == null || name.isBlank() || name.length() > 255
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid private attachment filename");
        }
        FilePathUtils.validateFileName(name);
        if (content == null || content.length == 0 || content.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Private attachments must be between 1 byte and 10 MiB");
        }
        // Detect from bytes, not client MIME or filename. Downloads are always attachments.
        String type = FileTypeUtils.getMineType(content);
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.getOrDefault(type, Set.of()).contains(extension)) {
            throw new IllegalArgumentException("Private attachments allow matching PDF, PNG, JPEG or TXT files only");
        }
        return type;
    }

}
