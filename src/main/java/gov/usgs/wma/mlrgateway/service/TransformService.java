package gov.usgs.wma.mlrgateway.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.exception.HystrixBadRequestException;

import gov.usgs.wma.mlrgateway.StepReport;
import gov.usgs.wma.mlrgateway.client.LegacyTransformerClient;
import gov.usgs.wma.mlrgateway.controller.WorkflowController;

@Service
public class TransformService {

	private LegacyTransformerClient legacyTransformerClient;

	protected static final String STEP_NAME = "Transform Data";
	protected static final String GEO_SUCCESS = "Decimal Location Transformed Successfully.";
	protected static final String GEO_FAILURE = "{\"error\":{\"message\": \"Unable to read transformer decimal_location output.\"}}";
	protected static final String STATION_IX_SUCCESS = "StationIX Tranformed Successfully.";
	protected static final String STATION_IX_FAILURE = "{\"error\":{\"message\": \"Unable to read transformer station_ix output.\"}}";
	protected static final String LATITUDE = "latitude";
	protected static final String LONGITUDE = "longitude";
	protected static final String COORDINATE_DATUM_CODE = "coordinateDatumCode";
	protected static final String STATION_NAME = "stationName";

	@Autowired
	public TransformService(LegacyTransformerClient legacyTransformerClient) {
		this.legacyTransformerClient = legacyTransformerClient;
	}

	public Map<String, Object> transform(Map<String, Object> ml) throws HystrixBadRequestException {
		Map<String, Object> transformed = new HashMap<>();
		transformed.putAll(ml);

		if (ml.containsKey(LATITUDE) && ml.containsKey(LONGITUDE) && ml.containsKey(COORDINATE_DATUM_CODE)) {
			transformed.putAll(transformGeo(ml));
		}

		if (ml.containsKey(STATION_NAME)) {
			transformed.putAll(transformStationIx(ml));
		}

		return transformed;	
	}

	protected Map<String, Object> transformGeo(Map<String, Object> ml) {
		String json = "{\"" + LATITUDE + "\": \"" + ml.get(LATITUDE) + "\",\"" + LONGITUDE + "\":\"" + ml.get(LONGITUDE) + "\",\"" + COORDINATE_DATUM_CODE + "\":\"" + ml.get(COORDINATE_DATUM_CODE) + "\"}";
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> transforms = new HashMap<>();
		TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {};

		ResponseEntity<String> response = legacyTransformerClient.decimalLocation(json);

		try {
			transforms = mapper.readValue(response.getBody(), mapType);
			WorkflowController.addStepReport(new StepReport(STEP_NAME, HttpStatus.SC_OK, GEO_SUCCESS, ml.get(LegacyWorkflowService.AGENCY_CODE), ml.get(LegacyWorkflowService.SITE_NUMBER)));
		} catch (Exception e) {
			WorkflowController.addStepReport(new StepReport(STEP_NAME, HttpStatus.SC_INTERNAL_SERVER_ERROR, GEO_FAILURE, ml.get(LegacyWorkflowService.AGENCY_CODE), ml.get(LegacyWorkflowService.SITE_NUMBER)));
		}

		return transforms;
	}

	protected Map<String, Object> transformStationIx(Map<String, Object> ml) {
		String json = "{\"" + STATION_NAME + "\": \"" + ml.get(STATION_NAME) + "\"}";
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> transforms = new HashMap<>();
		TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {};

		ResponseEntity<String> response = legacyTransformerClient.stationIx(json);

		try {
			transforms = mapper.readValue(response.getBody(), mapType);
			WorkflowController.addStepReport(new StepReport(STEP_NAME, HttpStatus.SC_OK, STATION_IX_SUCCESS, ml.get(LegacyWorkflowService.AGENCY_CODE), ml.get(LegacyWorkflowService.SITE_NUMBER)));
		} catch (Exception e) {
			WorkflowController.addStepReport(new StepReport(STEP_NAME, HttpStatus.SC_INTERNAL_SERVER_ERROR, STATION_IX_FAILURE, ml.get(LegacyWorkflowService.AGENCY_CODE), ml.get(LegacyWorkflowService.SITE_NUMBER)));
		}

		return transforms;
	}

}
