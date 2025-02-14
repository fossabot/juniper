package bio.terra.pearl.api.admin.service.kit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.pearl.api.admin.BaseSpringBootTest;
import bio.terra.pearl.api.admin.service.AuthUtilService;
import bio.terra.pearl.core.model.EnvironmentName;
import bio.terra.pearl.core.model.admin.AdminUser;
import bio.terra.pearl.core.service.exception.PermissionDeniedException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

public class KitExtServiceTests extends BaseSpringBootTest {
  @Autowired private KitExtService kitExtService;

  @Test
  @Transactional
  public void testRequestKitsRequiresAdmin() {
    when(mockAuthUtilService.authUserToStudy(any(), any(), any()))
        .thenThrow(new PermissionDeniedException(""));
    AdminUser adminUser = new AdminUser();
    assertThrows(
        PermissionDeniedException.class,
        () ->
            kitExtService.requestKits(
                adminUser,
                "someportal",
                "somestudy",
                EnvironmentName.sandbox,
                Arrays.asList("enrollee1", "enrollee2"),
                "kitType"));
  }

  @Test
  @Transactional
  public void testGetKitRequestsForStudyEnvironmentRequiresAdmin() {
    when(mockAuthUtilService.authUserToStudy(any(), any(), any()))
        .thenThrow(new PermissionDeniedException(""));
    AdminUser adminUser = new AdminUser();
    assertThrows(
        PermissionDeniedException.class,
        () ->
            kitExtService.getKitRequestsByStudyEnvironment(
                adminUser, "someportal", "somestudy", EnvironmentName.sandbox));
  }

  @Transactional
  @Test
  public void testRefreshKitStatusesAuthsStudy() {
    when(mockAuthUtilService.authUserToStudy(any(), any(), any()))
        .thenThrow(new PermissionDeniedException(""));
    AdminUser adminUser = new AdminUser();
    assertThrows(
        PermissionDeniedException.class,
        () ->
            kitExtService.refreshKitStatuses(
                adminUser, "someportal", "somestudy", EnvironmentName.irb));
  }

  @MockBean private AuthUtilService mockAuthUtilService;
}
