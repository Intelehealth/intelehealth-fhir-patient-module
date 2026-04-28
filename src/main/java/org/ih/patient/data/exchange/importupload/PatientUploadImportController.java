package org.ih.patient.data.exchange.importupload;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/patientinfo/rest/v1/patient/import")
public class PatientUploadImportController {

	@Autowired
	private PatientUploadImportService uploadImportService;

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PatientUploadImportResponse> uploadPatientJson(@RequestParam("file") MultipartFile file)
			throws Exception {
		return ResponseEntity.ok(uploadImportService.importPatientFile(file));
	}
}
