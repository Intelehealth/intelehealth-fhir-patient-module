package org.ih.patient.data.exchange.api;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import org.ih.patient.data.exchange.dataimports.ImportPatientService;
import org.ih.patient.data.exchange.domain.ImportResponse;
import org.ih.patient.data.exchange.dto.SearchPateintDTO;
import org.ih.patient.data.exchange.search.PatientSearchParam;
import org.ih.patient.data.exchange.search.PatientSearchService;
import org.ih.patient.data.exchange.service.BundleService;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/patientinfo/rest/v1/patient")
public class PatientRestController {

	@Autowired
	private ImportPatientService patientService;
	
	@Autowired
	private PatientSearchService patientSearchService;
	
	@Autowired
	private BundleService bundleService;

	@GetMapping("/import/{uuid}")
	public ResponseEntity<ImportResponse> reschedule(@PathVariable("uuid") String uuid)
			throws ParseException, JSONException, IOException {
		patientService.importPatient(uuid, "");
		ImportResponse res = new ImportResponse();
		res.setMessage("ok");
		res.setUuid(uuid);
		return new ResponseEntity<>(res, HttpStatus.OK);
	}

	@GetMapping("/import/{uuid}/{locationUUid}")
	public ResponseEntity<ImportResponse> patientImport(@PathVariable("uuid") String uuid,
			@PathVariable("locationUUid") String locationUUid) throws ParseException, JSONException, IOException {
		patientService.importPatient(uuid, locationUUid);
		ImportResponse res = new ImportResponse();
		res.setMessage("ok");
		res.setUuid(uuid);
		return new ResponseEntity<>(res, HttpStatus.OK);
	}

	@PostMapping("/search")
	public ResponseEntity<List<SearchPateintDTO>> searchPatient(@RequestBody PatientSearchParam param)
			throws UnsupportedEncodingException, ParseException, JSONException {
		return new ResponseEntity<>(patientSearchService.searchPatient(param), HttpStatus.OK);
	}
	
	@GetMapping(value="/{resourceType}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> searchPatient(
			@PathVariable("resourceType") String resourceType,
			@RequestParam Map<String, String> reqParam)
			throws UnsupportedEncodingException, ParseException, JSONException {

		return new ResponseEntity<>(
				bundleService.search(resourceType, reqParam), HttpStatus.OK);
	}

}
