package cn.iocoder.yudao.module.infra.service.file;

public interface PrivateFileService {

    int MAX_FILE_SIZE = 10 * 1024 * 1024;

    String upload(String objectType, String objectId, String purpose, String name, byte[] content);

    Download download(String id);

    // Only returned after both subject/tenant and object authorization succeed.
    record Download(String name, byte[] content) { }

}
