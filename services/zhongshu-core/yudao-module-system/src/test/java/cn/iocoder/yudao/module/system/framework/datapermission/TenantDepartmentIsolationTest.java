package cn.iocoder.yudao.module.system.framework.datapermission;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Executes SQL rewritten by the existing tenant and department interceptors against an H2
 * fixture. This proves technical isolation, not D-09 franchise/organization semantics or PG.
 */
class TenantDepartmentIsolationTest {

    private Connection connection;
    private PermissionCommonApi permissions;
    private TenantLineInnerInterceptor tenant;
    private DataPermissionInterceptor department;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:isolation_" + UUID.randomUUID() + ";DATABASE_TO_UPPER=false");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE system_users (id bigint PRIMARY KEY, tenant_id bigint, dept_id bigint)");
            statement.execute("INSERT INTO system_users VALUES (101,10,100),(102,10,100),(103,10,200),(201,20,100),(202,20,200)");
        }
        // Use production table metadata and handlers, rather than hard-coding WHERE predicates.
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "isolation-fixture");
        assistant.setCurrentNamespace("isolation-fixture");
        TableInfoHelper.initTableInfo(assistant, AdminUserDO.class);
        tenant = new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()));
        permissions = mock(PermissionCommonApi.class);
        DeptDataPermissionRule rule = new DeptDataPermissionRule(permissions);
        rule.addDeptColumn("system_users", "dept_id");
        rule.addUserColumn("system_users", "id");
        DataPermissionRuleFactory factory = mock(DataPermissionRuleFactory.class);
        when(factory.getDataPermissionRule(anyString())).thenReturn(List.of(rule));
        department = new DataPermissionInterceptor(new DataPermissionRuleHandler(factory));
        login();
        scope(new DeptDataPermissionRespDTO().setDeptIds(Set.of(100L)).setSelf(false));
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        connection.close();
    }

    @Test
    void authorizedDepartmentWithinTechnicalTenantIsVisible() throws Exception {
        assertEquals(List.of(101L, 102L), select("SELECT u.id FROM system_users u ORDER BY u.id"));
    }

    @Test
    void knownOtherDepartmentIdIsDenied() throws Exception {
        assertEquals(List.of(), select("SELECT u.id FROM system_users u WHERE u.id = 103"));
    }

    @Test
    void knownOtherTenantIdIsDeniedEvenWithSameDepartmentNumber() throws Exception {
        assertEquals(List.of(), select("SELECT u.id FROM system_users u WHERE u.id = 201"));
    }

    @Test
    void batchAndOrConditionsDoNotBypassEitherBoundary() throws Exception {
        assertEquals(List.of(101L), select("SELECT u.id FROM system_users u WHERE u.id IN (101,103,201) ORDER BY u.id"));
        assertEquals(List.of(), select("SELECT u.id FROM system_users u WHERE u.id = 103 OR u.id = 201"));
    }

    @Test
    void selfScopeDoesNotRevealKnownColleagueId() throws Exception {
        scope(new DeptDataPermissionRespDTO().setSelf(true));
        assertEquals(List.of(101L), select("SELECT u.id FROM system_users u ORDER BY u.id"));
        assertEquals(List.of(), select("SELECT u.id FROM system_users u WHERE u.id = 102"));
    }

    @Test
    void allScopeStillRespectsTenantBoundary() throws Exception {
        // Keep existing ALL semantics; do not pretend ALL grants franchise or field authorization.
        scope(new DeptDataPermissionRespDTO().setAll(true));
        assertEquals(List.of(101L, 102L, 103L), select("SELECT u.id FROM system_users u ORDER BY u.id"));
        assertEquals(List.of(), select("SELECT u.id FROM system_users u WHERE u.id = 201"));
    }

    @Test
    void noScopeReturnsNoRows() throws Exception {
        scope(new DeptDataPermissionRespDTO());
        assertEquals(List.of(), select("SELECT u.id FROM system_users u"));
    }

    @Test
    void nextRequestObservesRevokedDepartmentScope() throws Exception {
        assertEquals(List.of(101L, 102L), select("SELECT u.id FROM system_users u ORDER BY u.id"));
        login(); // Production cache is per LoginUser/request; do not reuse the old request context.
        scope(new DeptDataPermissionRespDTO());
        assertEquals(List.of(), select("SELECT u.id FROM system_users u"));
    }

    @Test
    void updateKnownOtherDepartmentAndDeleteOtherTenantAffectNoRows() throws Exception {
        assertEquals(0, update("UPDATE system_users u SET dept_id = 100 WHERE u.id = 103"));
        assertEquals(0, update("DELETE FROM system_users u WHERE u.id = 201"));
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM system_users WHERE id IN (103,201)")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
        }
    }

    private List<Long> select(String sql) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(rewrite(sql))) {
            while (rows.next()) {
                ids.add(rows.getLong(1));
            }
        }
        return ids;
    }

    private int update(String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(rewrite(sql));
        }
    }

    private String rewrite(String sql) {
        return department.parserSingle(tenant.parserSingle(sql, "isolation-fixture"), "isolation-fixture");
    }

    private void scope(DeptDataPermissionRespDTO scope) {
        when(permissions.getDeptDataPermission(101L)).thenReturn(scope);
    }

    private static void login() {
        LoginUser user = new LoginUser().setId(101L).setTenantId(10L).setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        TenantContextHolder.setTenantId(10L);
    }

}
