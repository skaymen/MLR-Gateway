package gov.usgs.wma.mlrgateway.service;

import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.exception.HystrixBadRequestException;

import gov.usgs.wma.mlrgateway.FeignBadResponseWrapper;
import gov.usgs.wma.mlrgateway.StepReport;
import gov.usgs.wma.mlrgateway.client.FileExportClient;
import gov.usgs.wma.mlrgateway.client.LegacyCruClient;
import gov.usgs.wma.mlrgateway.controller.ExportWorkflowController;

@Service
public class ExportWorkflowService {

	private LegacyCruClient legacyCruClient;
	private FileExportClient fileExportClient;

	public static final String AGENCY_CODE = "agencyCode";
	public static final String SITE_NUMBER = "siteNumber";
	public static final String SITE_GET_STEP = "Location Get by AgencyCode and SiteNumber";
	public static final String SITE_GET_SUCCESSFULL = "Location Get Successful";
	public static final String SITE_GET_DOES_NOT_EXIST = "Requested Location Not Found";
	public static final String EXPORT_ADD_STEP = "Export MLR Transaction File";
	public static final String EXPORT_SUCCESSFULL = "MLR Transaction File created Successfully.";
	
	protected static final String INTERNAL_ERROR_MESSAGE = "{\"error\":{\"message\": \"Unable to read Legacy CRU output.\"}}";
	protected static final String SC_INTERNAL_ERROR_MESSAGE = "{\"error_message\": \"Unable to serialize Legacy CRU output.\"}";

	@Autowired
	public ExportWorkflowService(LegacyCruClient legacyCruClient, FileExportClient fileExportClient) {
		this.legacyCruClient = legacyCruClient;
		this.fileExportClient = fileExportClient;
	}

	public void completeWorkflow(String agencyCode, String siteNumber) throws HystrixBadRequestException {
		addTransaction(agencyCode, siteNumber);
	}


	protected void addTransaction(String agencyCode, String siteNumber) {
		List<Map<String, Object>> sites = null;
		ObjectMapper mapper = new ObjectMapper();
		TypeReference<List<Map<String, Object>>> mapType = new TypeReference<List<Map<String, Object>>>() {};
		String json = "";
		
		ResponseEntity<String> cruResp = legacyCruClient.getMonitoringLocations(agencyCode, siteNumber);
		int cruStatus = cruResp.getStatusCodeValue();
		
		try {
			sites = mapper.readValue(cruResp.getBody(), mapType);
		} catch (Exception e) {
			int status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
			ExportWorkflowController.addStepReport(new StepReport(SITE_GET_STEP, status, INTERNAL_ERROR_MESSAGE, null, null));
			throw new FeignBadResponseWrapper(status, null, INTERNAL_ERROR_MESSAGE);
		}
		
		if (sites.isEmpty()) {
			ExportWorkflowController.addStepReport(new StepReport(SITE_GET_STEP, cruStatus, 200 == cruStatus ? SITE_GET_DOES_NOT_EXIST : cruResp.getBody(), agencyCode, siteNumber));
		}
		else {
			ExportWorkflowController.addStepReport(new StepReport(SITE_GET_STEP, cruStatus, 200 == cruStatus ? SITE_GET_SUCCESSFULL : cruResp.getBody(), agencyCode, siteNumber));

			try {
				json = mapper.writeValueAsString(sites.get(0));
			} catch (Exception e) {
				// Unable to determine when this might actually happen, but the api says it can...
				throw new FeignBadResponseWrapper(HttpStatus.SC_INTERNAL_SERVER_ERROR, null, SC_INTERNAL_ERROR_MESSAGE);
			}
				
			ResponseEntity<String> exportResp = fileExportClient.exportAdd(json);
			int exportStatus = exportResp.getStatusCodeValue();
			ExportWorkflowController.addStepReport(new StepReport(EXPORT_ADD_STEP, exportStatus, 200 == exportStatus ? EXPORT_SUCCESSFULL : exportResp.getBody(), agencyCode, siteNumber));
		}
	}

}
