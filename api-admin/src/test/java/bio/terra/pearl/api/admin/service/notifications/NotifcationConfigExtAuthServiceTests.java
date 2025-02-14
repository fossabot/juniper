package bio.terra.pearl.api.admin.service.notifications;

import static org.mockito.Mockito.when;

import bio.terra.pearl.api.admin.service.AuthUtilService;
import bio.terra.pearl.core.dao.publishing.PortalEnvironmentChangeRecordDao;
import bio.terra.pearl.core.model.EnvironmentName;
import bio.terra.pearl.core.model.admin.AdminUser;
import bio.terra.pearl.core.service.exception.PermissionDeniedException;
import bio.terra.pearl.core.service.notification.NotificationConfigService;
import bio.terra.pearl.core.service.portal.PortalEnvironmentService;
import bio.terra.pearl.core.service.study.StudyEnvironmentService;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@ContextConfiguration(classes = NotificationConfigExtService.class)
@WebMvcTest
public class NotifcationConfigExtAuthServiceTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private NotificationConfigExtService notificationConfigExtService;

  @MockBean private AuthUtilService mockAuthUtilService;

  @MockBean private NotificationConfigService notificationConfigService;
  @MockBean private StudyEnvironmentService studyEnvironmentService;
  @MockBean private PortalEnvironmentService portalEnvironmentService;
  @MockBean private PortalEnvironmentChangeRecordDao portalEnvironmentChangeRecordDao;

  @Test
  public void replaceConfigAuthsToPortal() {
    AdminUser user = AdminUser.builder().superuser(false).build();
    when(mockAuthUtilService.authUserToPortal(user, "foo"))
        .thenThrow(new PermissionDeniedException("test1"));
    Assertions.assertThrows(
        PermissionDeniedException.class,
        () ->
            notificationConfigExtService.replace(
                "foo", "studyCode", EnvironmentName.live, UUID.randomUUID(), null, user));
  }

  @Test
  public void findForSutdyAuthsToStudy() {
    AdminUser user = AdminUser.builder().superuser(false).build();
    when(mockAuthUtilService.authUserToStudy(user, "foo", "bar"))
        .thenThrow(new PermissionDeniedException("test1"));
    Assertions.assertThrows(
        PermissionDeniedException.class,
        () -> notificationConfigExtService.findForStudy(user, "foo", "bar", EnvironmentName.live));
  }
}
