package org.ih.patient.data.exchange.export;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.parser.DataFormatException;

@RestController
@RequestMapping("/patientinfo/rest/v1/patient/export")
public class CreatedPatientExportController {

	@Autowired
	private CreatedPatientExportService exportService;

	@GetMapping(value = "/created", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> exportCreatedPatients(@RequestParam("startDate") String startDate,
			@RequestParam("endDate") String endDate)
			throws ConfigurationException, DataFormatException, IOException {
		CreatedPatientExportResult result = exportService.exportCreatedPatients(startDate, endDate);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setContentDisposition(ContentDisposition.attachment()
				.filename("created-patients-" + startDate + "-to-" + endDate + ".json")
				.build());
		headers.add("X-Total-Patients", String.valueOf(result.getTotalPatients()));
		headers.add("X-Exported-Patients", String.valueOf(result.getExportedPatients()));
		headers.add("X-Validation-Failed-Patients", String.valueOf(result.getValidationFailedPatients()));

		return new ResponseEntity<>(result.getPayload(), headers, HttpStatus.OK);
	}
}
