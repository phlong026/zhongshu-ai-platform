package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.PrivateFileDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrivateFileMapper extends BaseMapperX<PrivateFileDO> {

    default PrivateFileDO selectMetadata(String id, Long tenantId) {
        // Explicit tenant predicate remains even if an outer caller ignores the tenant plugin.
        return selectOne(new LambdaQueryWrapperX<PrivateFileDO>()
                .eq(PrivateFileDO::getId, id).eq(PrivateFileDO::getTenantId, tenantId));
    }

    default PrivateFileDO selectContent(String id, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<PrivateFileDO>()
                .select(PrivateFileDO::getId, PrivateFileDO::getTenantId, PrivateFileDO::getContent)
                .eq(PrivateFileDO::getId, id).eq(PrivateFileDO::getTenantId, tenantId));
    }

}
