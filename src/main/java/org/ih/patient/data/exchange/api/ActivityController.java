package org.ih.patient.data.exchange.api;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;

import org.ih.patient.data.exchange.datatype.ConfigFacilityDataType;
import org.ih.patient.data.exchange.domain.ConfigDataSync;
import org.ih.patient.data.exchange.service.ConfigDataSyncService;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/patient/api/v1/control")
public class ActivityController {

	@Autowired
	private ConfigDataSyncService configDataSyncService;

	@GetMapping("/activity")
	public ResponseEntity<?> activity() throws ParseException, JSONException, IOException {
		ConfigDataSync patientSync = configDataSyncService.getConfigDataSync(ConfigFacilityDataType.PATIENTS);

		HashMap<String, Object> object = new HashMap<>();
		object.put("status", HttpStatus.OK);
		object.put("message", "Patient module is alive");
		object.put("responseTime", new Date());
		object.put("configDataSyncStatus", patientSync);

		return new ResponseEntity<>(object, HttpStatus.OK);
	}

}
