package gov.usgs.wma.mlrgateway.client;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;

import gov.usgs.wma.mlrgateway.config.PropagateBadRequest;

@FeignClient(name="notification", configuration=PropagateBadRequest.class)
public interface NotificationClient {
	public static final String MESSAGE_SUBJECT_KEY = "subject";
	public static final String MESSAGE_TO_KEY = "to";
	public static final String MESSAGE_FROM_KEY = "from";
	public static final String MESSAGE_TEXT_BODY_KEY = "textBody";
	public static final String MESSAGE_HTML_BODY_KEY = "htmlBody";
	public static final String MESSAGE_CC_KEY = "cc";
	public static final String MESSAGE_BCC_KEY = "bcc";
	public static final String MESSAGE_REPLY_TO_KEY = "replyTo";

	@RequestMapping(method=RequestMethod.POST, value="notification/email", consumes="application/json")
	ResponseEntity<String>  sendEmail(@RequestBody String messageJson);

}
