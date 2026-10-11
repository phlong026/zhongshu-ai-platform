package cn.iocoder.yudao.module.infra.dal.dataobject.file;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Small private attachments, deliberately separate from public FileDO / FileClient storage.
 * No configId, path or public URL is created for these bytes.
 */
@TableName("infra_private_file")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PrivateFileDO extends TenantBaseDO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String objectType;
    private String objectId;
    private String purpose;
    private String name;
    private String type;
    private Long size;

    // Metadata reads do not retrieve attachment bytes. Only the authorized read selects them.
    @TableField(select = false)
    @ToString.Exclude
    private byte[] content;

}
