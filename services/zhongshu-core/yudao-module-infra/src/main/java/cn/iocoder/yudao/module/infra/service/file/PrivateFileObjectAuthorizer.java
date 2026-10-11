package cn.iocoder.yudao.module.infra.service.file;

/**
 * Business-object authorization port. No implementation is installed by the foundation.
 *
 * A future adapter must verify that the object exists in the supplied technical tenant and
 * check the current subject's object/action/field policy, including attachment purpose.
 * It must resolve authoritative ownership itself, never trust ownership sent by the client.
 * D-09 business organizations are intentionally not inferred from tenant/dept identifiers.
 * Missing adapters, unknown object types and revoked access must fail closed.
 */
@FunctionalInterface
public interface PrivateFileObjectAuthorizer {

    boolean isAllowed(Access access);

    enum Action { UPLOAD, DOWNLOAD }

    record Access(Long tenantId, Long userId, String objectType, String objectId,
                  String purpose, Action action) { }

}
