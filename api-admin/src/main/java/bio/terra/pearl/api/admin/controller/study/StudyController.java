package bio.terra.pearl.api.admin.controller.study;

import bio.terra.pearl.api.admin.api.StudyApi;
import bio.terra.pearl.api.admin.service.AuthUtilService;
import bio.terra.pearl.api.admin.service.study.StudyExtService;
import bio.terra.pearl.core.model.admin.AdminUser;
import bio.terra.pearl.core.model.study.Study;
import bio.terra.pearl.core.service.study.StudyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class StudyController implements StudyApi {
  private final AuthUtilService requestService;
  private final HttpServletRequest request;
  private final StudyExtService studyExtService;
  private final ObjectMapper objectMapper;

  public StudyController(
      AuthUtilService requestService,
      HttpServletRequest request,
      StudyExtService studyExtService,
      StudyService studyService,
      ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.requestService = requestService;
    this.request = request;
    this.studyExtService = studyExtService;
  }

  @Override
  public ResponseEntity<Object> create(String portalShortcode, Object body) {
    AdminUser operator = requestService.requireAdminUser(request);
    var studyDto = objectMapper.convertValue(body, StudyExtService.StudyCreationDto.class);
    Study study = studyExtService.create(portalShortcode, studyDto, operator);
    return ResponseEntity.ok(study);
  }
}
