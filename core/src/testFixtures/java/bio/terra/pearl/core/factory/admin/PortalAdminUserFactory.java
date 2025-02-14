package bio.terra.pearl.core.factory.admin;

import java.util.ArrayList;
import java.util.List;

import bio.terra.pearl.core.factory.portal.PortalFactory;
import bio.terra.pearl.core.model.admin.AdminUser;
import bio.terra.pearl.core.model.admin.PortalAdminUser;
import bio.terra.pearl.core.model.portal.Portal;
import bio.terra.pearl.core.service.admin.PortalAdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PortalAdminUserFactory {

    @Autowired
    private AdminUserFactory adminUserFactory;

    @Autowired
    private PortalAdminUserService portalAdminUserService;

    @Autowired
    private PortalFactory portalFactory;

    public PortalAdminUser.PortalAdminUserBuilder builder(String testName) {
        return PortalAdminUser.builder();
    }

    public PortalAdminUser.PortalAdminUserBuilder builderWithDependencies(String testName) {
        var adminUser = adminUserFactory.buildPersisted(testName);
        var portal = portalFactory.buildPersisted(testName);
        return PortalAdminUser.builder().adminUserId(adminUser.getId()).portalId(portal.getId());
    }

    public PortalAdminUser buildPersisted(String testName) {
        var portalAdminUser = builderWithDependencies(testName).build();
        return portalAdminUserService.create(portalAdminUser);
    }

    public List<PortalAdminUser> buildPersistedWithPortals(String testName, List<Portal> portals) {
        AdminUser adminUser = adminUserFactory.buildPersisted(testName);
        List<PortalAdminUser> portalUsers = new ArrayList<>();
        for (var portal: portals) {
            portalUsers.add(portalAdminUserService.create(PortalAdminUser.builder()
                    .adminUserId(adminUser.getId())
                    .portalId(portal.getId())
                    .build()));
        }
        return portalUsers;
    }
}
