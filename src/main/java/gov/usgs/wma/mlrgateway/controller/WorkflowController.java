package gov.usgs.wma.mlrgateway.controller;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import gov.usgs.wma.mlrgateway.controller.BaseController;
import gov.usgs.wma.mlrgateway.FeignBadResponseWrapper;
import gov.usgs.wma.mlrgateway.GatewayReport;
import gov.usgs.wma.mlrgateway.StepReport;
import gov.usgs.wma.mlrgateway.service.LegacyWorkflowService;
import gov.usgs.wma.mlrgateway.service.NotificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api(tags={"Workflow"})
@RestController
@RequestMapping("/workflows")
public class WorkflowController extends BaseController {

	@Value("${temporaryNotificationEmail}")
	private String temporaryNotificationEmail;

	private LegacyWorkflowService legacy;
	private NotificationService notificationService;
	public static final String COMPLETE_WORKFLOW_SUBJECT = "MLR Report for Submitted Ddot Transaction";
	public static final String VALIDATE_DDOT_WORKFLOW_SUBJECT = "MLR Report for Submitted Ddot Validation";

	@Autowired
	public WorkflowController(LegacyWorkflowService legacy, NotificationService notificationService) {
		this.legacy = legacy;
		this.notificationService = notificationService;
	}

	@ApiOperation(value="Perform the entire workflow, including updating the repository and sending transaction file(s) to WSC.")
	@ApiResponses(value={@ApiResponse(code=200, message="OK"),
			@ApiResponse(code=400, message="Bad Request"),
			@ApiResponse(code=401, message="Unauthorized"),
			@ApiResponse(code=403, message="Forbidden")})
	@PostMapping("/ddots")
	public GatewayReport legacyWorkflow(@RequestPart MultipartFile file, HttpServletResponse response) {
		setReport(new GatewayReport(LegacyWorkflowService.COMPLETE_WORKFLOW));
		try {
			legacy.completeWorkflow(file);
		} catch (Exception e) {
			if (e instanceof FeignBadResponseWrapper) {
				int status = ((FeignBadResponseWrapper) e).getStatus();
				WorkflowController.addStepReport(new StepReport(LegacyWorkflowService.COMPLETE_WORKFLOW, status, ((FeignBadResponseWrapper) e).getBody(), null, null));
			} else {
				int status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
				WorkflowController.addStepReport(new StepReport(LegacyWorkflowService.COMPLETE_WORKFLOW, status, e.getLocalizedMessage(), null, null));
			}
		}
		
		//Send Notification
		notificationStep(VALIDATE_DDOT_WORKFLOW_SUBJECT);
		
		//Return report
		GatewayReport rtn = getReport();
		response.setStatus(rtn.getStatus());
		remove();
		return rtn;
	}

	@ApiOperation(value="Validate a D dot file, DOES NOT update the repository or send transaction file(s) to WSC.")
	@ApiResponses(value={@ApiResponse(code=200, message="OK"),
			@ApiResponse(code=400, message="Bad Request"),
			@ApiResponse(code=401, message="Unauthorized")})
	@PostMapping("/ddots/validate")
	public GatewayReport legacyValidationWorkflow(@RequestPart MultipartFile file, HttpServletResponse response) {
		setReport(new GatewayReport(LegacyWorkflowService.VALIDATE_DDOT_WORKFLOW));
		try {
			legacy.ddotValidation(file);
		} catch (Exception e) {
			if (e instanceof FeignBadResponseWrapper) {
				int status = ((FeignBadResponseWrapper) e).getStatus();
				WorkflowController.addStepReport(new StepReport(LegacyWorkflowService.VALIDATE_DDOT_WORKFLOW, status, ((FeignBadResponseWrapper) e).getBody(), null, null));
			} else {
				int status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
				WorkflowController.addStepReport(new StepReport(LegacyWorkflowService.VALIDATE_DDOT_WORKFLOW, status, e.getLocalizedMessage(), null, null));
			}
		}
		
		//Send Notification
		notificationStep(VALIDATE_DDOT_WORKFLOW_SUBJECT);
		
		//Return report
		GatewayReport rtn = getReport();
		response.setStatus(rtn.getStatus());
		remove();
		return rtn;
	}
	
	private int notificationStep(String subject) {
		int status = -1;
		
		//Send Notification
		try {
			notificationService.sendNotification(temporaryNotificationEmail, subject, getReport().toString());
		} catch(Exception e) {
			if (e instanceof FeignBadResponseWrapper) {
				 status = ((FeignBadResponseWrapper) e).getStatus();
				WorkflowController.addStepReport(new StepReport(NotificationService.NOTIFICATION_STEP, status, ((FeignBadResponseWrapper) e).getBody(), null, null));
			} else {
				status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
				WorkflowController.addStepReport(new StepReport(NotificationService.NOTIFICATION_STEP, status, e.getLocalizedMessage(), null, null));
			}
		}
		
		return status;
	}
}
